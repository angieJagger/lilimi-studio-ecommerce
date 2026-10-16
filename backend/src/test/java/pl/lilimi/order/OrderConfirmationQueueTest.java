package pl.lilimi.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderContactRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.application.OrderSubmissionService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OrderConfirmationQueueTest {

  @Autowired
  private OrderSubmissionService submissionService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private final UUID key = UUID.randomUUID();

  @AfterEach
  void cleanUp() {
    var orderIds = jdbcTemplate.queryForList("""
        SELECT order_id
        FROM order_submissions
        WHERE idempotency_key = ?
          AND order_id IS NOT NULL
        """,
      UUID.class,
      key
    );

    jdbcTemplate.update("""
        DELETE FROM order_submissions
        WHERE idempotency_key = ?
        """,
      key
    );

    for (var orderId : orderIds) {
      jdbcTemplate.update(
        "DELETE FROM order_items WHERE order_id = ?",
        orderId
      );

      // The queue entry is removed by ON DELETE CASCADE.
      jdbcTemplate.update(
        "DELETE FROM orders WHERE id = ?",
        orderId
      );
    }
  }

  @Test
  void shouldCommitPendingEmailTogetherWithOrder() {
    var order = submissionService.submit(key, request());

    var entry = jdbcTemplate.queryForMap("""
        SELECT status, attempts, sent_at, last_error_code
        FROM order_confirmation_emails
        WHERE order_id = ?
        """,
      order.getId()
    );

    assertThat(entry.get("status")).isEqualTo("pending");
    assertThat(entry.get("attempts")).isEqualTo(0);
    assertThat(entry.get("sent_at")).isNull();
    assertThat(entry.get("last_error_code")).isNull();
    assertThat(queueCount(order.getId())).isEqualTo(1L);
  }

  @Test
  void shouldNotCreateAnotherEmailForRepeatedSubmission() {
    var first = submissionService.submit(key, request());
    var repeated = submissionService.submit(key, request());

    assertThat(repeated.getId()).isEqualTo(first.getId());
    assertThat(queueCount(first.getId())).isEqualTo(1L);

    var attempts = jdbcTemplate.queryForObject("""
        SELECT attempts
        FROM order_confirmation_emails
        WHERE order_id = ?
        """,
      Integer.class,
      first.getId()
    );

    assertThat(attempts).isZero();
  }

  @Test
  void shouldRollBackOrderAndQueueEntryTogether() {
    var orderId = new AtomicReference<UUID>();
    var transaction = new TransactionTemplate(
      transactionManager
    );

    transaction.executeWithoutResult(status -> {
      var order = submissionService.submit(key, request());
      orderId.set(order.getId());

      assertThat(queueCount(order.getId())).isEqualTo(1L);

      status.setRollbackOnly();
    });

    assertThat(orderId.get()).isNotNull();
    assertThat(queueCount(orderId.get())).isZero();

    var orderCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM orders
        WHERE id = ?
        """,
      Long.class,
      orderId.get()
    );

    var submissionCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM order_submissions
        WHERE idempotency_key = ?
        """,
      Long.class,
      key
    );

    assertThat(orderCount).isZero();
    assertThat(submissionCount).isZero();
  }

  private long queueCount(UUID orderId) {
    return jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM order_confirmation_emails
        WHERE order_id = ?
        """,
      Long.class,
      orderId
    );
  }

  private CreateOrderRequest request() {
    return new CreateOrderRequest(
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
  }
}
