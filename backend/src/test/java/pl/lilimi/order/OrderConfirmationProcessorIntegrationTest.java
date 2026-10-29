package pl.lilimi.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pl.lilimi.DigitalPackageTestData;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderContactRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.application.OrderConfirmationMailService;
import pl.lilimi.order.application.OrderConfirmationProcessor;
import pl.lilimi.order.application.OrderSubmissionService;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
  "spring.application.name=lilimi-order-confirmation-processor-test",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
class OrderConfirmationProcessorIntegrationTest {

  @Autowired
  private OrderSubmissionService submissionService;

  @Autowired
  private OrderConfirmationProcessor processor;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoBean
  private OrderConfirmationMailService mailService;

  @MockitoBean
  private Clock clock;

  private final UUID key = UUID.randomUUID();
  private Instant now;
  private UUID orderId;
  private UUID packageId;
  private boolean packageCreatedByTest;

  @BeforeEach
  void setUp() {
    var existingPackageIds = jdbcTemplate.queryForList(
      """
      SELECT id
      FROM digital_product_packages
      WHERE product_id = ?
        AND current_package = TRUE
      """,
      UUID.class,
      "pattern-001"
    );

    packageId = DigitalPackageTestData.currentPackageId(
      jdbcTemplate,
      "pattern-001"
    );

    packageCreatedByTest = existingPackageIds.isEmpty();
    now = Instant.now()
      .plusSeconds(60)
      .truncatedTo(ChronoUnit.MICROS);
    when(clock.instant()).thenReturn(now);
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
      "DELETE FROM order_submissions WHERE idempotency_key = ?",
      key
    );

    if (orderId != null) {
      jdbcTemplate.update(
        "DELETE FROM order_items WHERE order_id = ?",
        orderId
      );

      jdbcTemplate.update(
        "DELETE FROM orders WHERE id = ?",
        orderId
      );
    }
    if (packageCreatedByTest && packageId != null) {
      jdbcTemplate.update(
        "DELETE FROM digital_product_packages WHERE id = ?",
        packageId
      );
    }
  }

  @Test
  void shouldCommitSentStatusAndNotSendAgain() {
    createOrder();

    assertThat(processor.processNext()).isTrue();

    var entry = jdbcTemplate.queryForMap("""
        SELECT status, attempts, sent_at, last_error_code
        FROM order_confirmation_emails
        WHERE order_id = ?
        """,
      orderId
    );

    assertThat(entry.get("status")).isEqualTo("sent");
    assertThat(entry.get("attempts")).isEqualTo(1);

    assertThat(((Timestamp) entry.get("sent_at")).toInstant())
      .isEqualTo(now);

    assertThat(entry.get("last_error_code")).isNull();

    assertThat(processor.processNext()).isFalse();

    verify(mailService, times(1)).send(any());
  }

  @Test
  void shouldPersistRetryWithoutRemovingOrder() {
    createOrder();

    doThrow(new MailSendException("Simulated SMTP failure"))
      .when(mailService)
      .send(any());

    assertThat(processor.processNext()).isTrue();

    var entry = jdbcTemplate.queryForMap("""
        SELECT status, attempts, next_attempt_at,
               sent_at, last_error_code
        FROM order_confirmation_emails
        WHERE order_id = ?
        """,
      orderId
    );

    assertThat(entry.get("status")).isEqualTo("pending");
    assertThat(entry.get("attempts")).isEqualTo(1);
    assertThat(entry.get("sent_at")).isNull();

    assertThat(entry.get("last_error_code"))
      .isEqualTo("SMTP_SEND_FAILED");

    assertThat(
      ((Timestamp) entry.get("next_attempt_at")).toInstant()
    ).isEqualTo(now.plusSeconds(60));

    var ordersRemaining = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM orders
        WHERE id = ?
        """,
      Long.class,
      orderId
    );

    assertThat(ordersRemaining).isEqualTo(1L);

    // The retry is not due yet.
    assertThat(processor.processNext()).isFalse();

    verify(mailService, times(1)).send(any());
  }

  @Test
  void shouldPersistFailedStatusAfterFifthAttempt() {
    createOrder();

    jdbcTemplate.update("""
        UPDATE order_confirmation_emails
        SET attempts = 4
        WHERE order_id = ?
        """,
      orderId
    );

    doThrow(new MailSendException("Simulated SMTP failure"))
      .when(mailService)
      .send(any());

    assertThat(processor.processNext()).isTrue();

    var entry = jdbcTemplate.queryForMap("""
        SELECT status, attempts, sent_at, last_error_code
        FROM order_confirmation_emails
        WHERE order_id = ?
        """,
      orderId
    );

    assertThat(entry.get("status")).isEqualTo("failed");
    assertThat(entry.get("attempts")).isEqualTo(5);
    assertThat(entry.get("sent_at")).isNull();

    assertThat(entry.get("last_error_code"))
      .isEqualTo("SMTP_SEND_FAILED");

    assertThat(processor.processNext()).isFalse();

    verify(mailService, times(1)).send(any());
  }

  @Test
  void shouldSkipEntryLockedByAnotherProcessor() throws Exception {
    createOrder();

    var sendingStarted = new CountDownLatch(1);
    var releaseSending = new CountDownLatch(1);

    doAnswer(invocation -> {
      sendingStarted.countDown();

      if (!releaseSending.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
          "Timed out waiting to release SMTP simulation"
        );
      }

      return null;
    }).when(mailService).send(any());

    var executor = Executors.newFixedThreadPool(2);

    try {
      var first = executor.submit(() -> processor.processNext());

      assertThat(
        sendingStarted.await(10, TimeUnit.SECONDS)
      ).isTrue();

      var second = executor.submit(() -> processor.processNext());

      // This must finish while the first transaction holds the lock.
      assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();

      releaseSending.countDown();

      assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();

      verify(mailService, times(1)).send(any());

      var status = jdbcTemplate.queryForObject("""
          SELECT status
          FROM order_confirmation_emails
          WHERE order_id = ?
          """,
        String.class,
        orderId
      );

      assertThat(status).isEqualTo("sent");
    } finally {
      releaseSending.countDown();
      executor.shutdown();

      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        executor.shutdownNow();

        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException(
            "Processor threads did not terminate"
          );
        }
      }
    }
  }

  private void createOrder() {
    var request = new CreateOrderRequest(
      "pl",
      new OrderContactRequest(
        "Test Customer",
        "confirmation-test@example.com",
        null
      ),
      new OrderDeliveryRequest.Digital(),
      List.of(
        new OrderItemRequest.Digital("pattern-001", 1)
      )
    );

    orderId = submissionService.submit(key, request).getId();
  }
}
