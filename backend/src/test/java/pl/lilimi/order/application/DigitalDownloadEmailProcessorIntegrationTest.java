package pl.lilimi.order.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mail.MailSendException;

import pl.lilimi.DigitalPackageTestData;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.persistence.DigitalDownloadEmailRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false",
  "lilimi.mail.digital-downloads.enabled=false",
  "lilimi.public-base-url=https://lilimistudio.com",
  "lilimi.digital-downloads.token-validity=P7D"
})
@Import(PostgresTestConfiguration.class)
class DigitalDownloadEmailProcessorIntegrationTest {

  @Autowired
  private DigitalDownloadEmailProcessor processor;

  @Autowired
  private DigitalDownloadEmailRepository queue;

  @Autowired
  private DigitalDownloadTokenVerifier tokenVerifier;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private OrderItemRepository items;

  @Autowired
  private TransactionTemplate transactions;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private DigitalDownloadMailService mailService;

  @MockitoBean
  private PaymentAttemptRepository payments;

  @MockitoBean
  private Clock clock;

  private UUID orderId;
  private UUID packageId;
  private Instant now;

  @BeforeEach
  void setUp() {
    now = Instant.now()
      .plusSeconds(60)
      .truncatedTo(ChronoUnit.MICROS);

    when(clock.instant()).thenReturn(now);

    packageId = DigitalPackageTestData.createHistoricalPackage(
      jdbc,
      "pattern-001"
    );

    var order = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        "customer@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    ));

    orderId = order.getId();

    items.saveAndFlush(OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Leśny smok",
      packageId,
      2900
    ));

    when(payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    transactions.executeWithoutResult(status ->
      queue.enqueue(orderId)
    );
  }

  @AfterEach
  void cleanUp() {
    if (orderId != null) {
      jdbc.update(
        "DELETE FROM digital_download_emails WHERE order_id = ?",
        orderId
      );
      jdbc.update(
        "DELETE FROM digital_download_tokens WHERE order_id = ?",
        orderId
      );
      jdbc.update(
        "DELETE FROM order_items WHERE order_id = ?",
        orderId
      );
      jdbc.update(
        "DELETE FROM orders WHERE id = ?",
        orderId
      );
    }

    if (packageId != null) {
      jdbc.update(
        "DELETE FROM digital_product_packages WHERE id = ?",
        packageId
      );
    }
  }

  @Test
  void shouldCommitTokenReferencedBySentEmail() {
    assertThat(processor.processNext()).isTrue();

    var captor = ArgumentCaptor.forClass(
      DigitalDownloadEmailMessage.class
    );

    verify(mailService).send(captor.capture());

    var message = captor.getValue();

    assertThat(message.recipient())
      .isEqualTo("customer@example.com");

    var matcher = Pattern.compile(
      "https://lilimistudio\\.com/pl/downloads#token=([A-Za-z0-9_-]{43})"
    ).matcher(message.text());

    assertThat(matcher.find()).isTrue();

    String rawToken = matcher.group(1);

    // The processor transaction has already committed.
    assertThat(tokenVerifier.requireOrderId(rawToken))
      .isEqualTo(orderId);

    var entry = jdbc.queryForMap(
      """
      SELECT status, attempts, sent_at
      FROM digital_download_emails
      WHERE order_id = ?
      """,
      orderId
    );

    assertThat(entry.get("status")).isEqualTo("sent");
    assertThat(((Number) entry.get("attempts")).intValue())
      .isEqualTo(1);
    assertThat(entry.get("sent_at")).isNotNull();
  }

  @Test
  void shouldCommitRetryAndSendAfterSmtpRecovers() {
    doThrow(new MailSendException("Simulated SMTP failure"))
      .when(mailService)
      .send(org.mockito.ArgumentMatchers.any(
        DigitalDownloadEmailMessage.class
      ));

    assertThat(processor.processNext()).isTrue();

    var failedAttempt = jdbc.queryForMap(
      """
      SELECT status, attempts, last_error_code
      FROM digital_download_emails
      WHERE order_id = ?
      """,
      orderId
    );

    assertThat(failedAttempt.get("status")).isEqualTo("pending");
    assertThat(((Number) failedAttempt.get("attempts")).intValue())
      .isEqualTo(1);
    assertThat(failedAttempt.get("last_error_code"))
      .isEqualTo("SMTP_SEND_FAILED");

    // The retry is scheduled for one minute later.
    assertThat(processor.processNext()).isFalse();

    when(clock.instant()).thenReturn(now.plusSeconds(61));

    doNothing()
      .when(mailService)
      .send(org.mockito.ArgumentMatchers.any(
        DigitalDownloadEmailMessage.class
      ));

    assertThat(processor.processNext()).isTrue();

    var messages = ArgumentCaptor.forClass(
      DigitalDownloadEmailMessage.class
    );

    verify(mailService, times(2)).send(messages.capture());

    var matcher = Pattern.compile(
      "https://lilimistudio\\.com/pl/downloads#token=([A-Za-z0-9_-]{43})"
    ).matcher(messages.getAllValues().get(1).text());

    assertThat(matcher.find()).isTrue();
    assertThat(tokenVerifier.requireOrderId(matcher.group(1)))
      .isEqualTo(orderId);

    var sentAttempt = jdbc.queryForMap(
      """
      SELECT status, attempts, last_error_code
      FROM digital_download_emails
      WHERE order_id = ?
      """,
      orderId
    );

    assertThat(sentAttempt.get("status")).isEqualTo("sent");
    assertThat(((Number) sentAttempt.get("attempts")).intValue())
      .isEqualTo(2);
    assertThat(sentAttempt.get("last_error_code")).isNull();
  }
}
