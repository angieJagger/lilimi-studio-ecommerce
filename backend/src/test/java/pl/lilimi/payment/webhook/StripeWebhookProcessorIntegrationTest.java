package pl.lilimi.payment.webhook;

import com.stripe.Stripe;
import com.stripe.model.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.api.admin.ChangeOrderStatusRequest;
import pl.lilimi.order.application.admin.AdminOrderStatusService;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.cancellation.CheckoutExpirationQueue;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;
import pl.lilimi.order.persistence.OrderRepository;



import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
class StripeWebhookProcessorIntegrationTest {

  private static final String SECRET =
    "whsec_processor_integration_test";

  @Autowired
  private StripeWebhookProcessor processor;

  @Autowired
  private PaymentAttemptRepository attempts;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private AdminOrderStatusService orderStatus;
  @Autowired
  private CheckoutExpirationQueue expirationQueue;

  private final List<String> eventIds = new ArrayList<>();

  private UUID orderId;
  private UUID attemptId;
  private String sessionId;
  private Instant expiresAt;

  @BeforeEach
  void setUp() {
    var order = orders.saveAndFlush(
      new Order(
        "en",
        new OrderContact(
          "Anna Kowalska",
          "customer@example.com",
          null
        ),
        OrderDelivery.digital(),
        2900,
        0
      )
    );

    orderId = order.getId();
    sessionId = "cs_test_" + UUID.randomUUID();
    expiresAt = Instant.now().plusSeconds(1800);

    var attempt = attempts.saveAndFlush(
      new PaymentAttempt(
        orderId,
        UUID.randomUUID(),
        2900,
        "PLN",
        false,
        Instant.now().minusSeconds(60)
      )
    );

    attemptId = attempt.getId();
  }

  @AfterEach
  void cleanUp() {
    for (String eventId : eventIds) {
      jdbc.update(
        "DELETE FROM stripe_webhook_events WHERE event_id = ?",
        eventId
      );
    }

    if (orderId != null) {
      jdbc.update(
        """
        DELETE FROM checkout_expiration_tasks
        WHERE payment_attempt_id IN (
          SELECT id
          FROM order_payment_attempts
          WHERE order_id = ?
        )
        """,
        orderId
      );

      jdbc.update(
        "DELETE FROM order_payment_attempts WHERE order_id = ?",
        orderId
      );
      jdbc.update(
        "DELETE FROM orders WHERE id = ?",
        orderId
      );
    }
  }

  @Test
  void shouldConfirmPaymentBeforeCheckoutResponseIsSaved()
    throws Exception {

    var event = event(
      "checkout.session.completed",
      "complete",
      "paid",
      2900,
      "pi_test_confirmed"
    );

    processor.process(event);

    var saved = savedAttempt();

    assertThat(saved.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(saved.getStripeCheckoutSessionId())
      .isEqualTo(sessionId);
    assertThat(saved.getStripePaymentIntentId())
      .isEqualTo("pi_test_confirmed");
    assertThat(saved.getPaidAt())
      .isEqualTo(Instant.ofEpochSecond(event.getCreated()));
    assertThat(eventCount(event.getId())).isEqualTo(1L);
  }

  @Test
  void shouldIgnoreRepeatedEvent() throws Exception {
    var event = event(
      "checkout.session.completed",
      "complete",
      "paid",
      2900,
      "pi_test_duplicate"
    );

    processor.process(event);
    long firstVersion = savedAttempt().getVersion();

    processor.process(event);

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(savedAttempt().getVersion())
      .isEqualTo(firstVersion);
    assertThat(eventCount(event.getId())).isEqualTo(1L);
  }

  @Test
  void shouldKeepUnpaidCompletedSessionPending()
    throws Exception {

    var event = event(
      "checkout.session.completed",
      "complete",
      "unpaid",
      2900,
      null
    );

    processor.process(event);

    var saved = savedAttempt();

    assertThat(saved.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
    assertThat(saved.getPaidAt()).isNull();
    assertThat(saved.getStripePaymentIntentId()).isNull();
    assertThat(eventCount(event.getId())).isEqualTo(1L);
  }

  @Test
  void shouldAcceptLateSuccessAfterExpiration()
    throws Exception {

    processor.process(event(
      "checkout.session.expired",
      "expired",
      "unpaid",
      2900,
      null
    ));

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.EXPIRED);

    processor.process(event(
      "checkout.session.async_payment_succeeded",
      "complete",
      "paid",
      2900,
      "pi_test_late"
    ));

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(savedAttempt().getStripePaymentIntentId())
      .isEqualTo("pi_test_late");
  }

  @Test
  void shouldRejectIncorrectAmountWithoutSavingEvent()
    throws Exception {

    var event = event(
      "checkout.session.completed",
      "complete",
      "paid",
      100,
      "pi_test_wrong_amount"
    );

    assertInvalid(event);

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(eventCount(event.getId())).isZero();
  }

  @Test
  void shouldRollBackEventAndSessionWhenPaymentIntentIsMissing()
    throws Exception {

    var event = event(
      "checkout.session.completed",
      "complete",
      "paid",
      2900,
      null
    );

    // Processing inserts the event and attaches the session,
    // then fails because the payment intent is missing.
    assertInvalid(event);

    var saved = savedAttempt();

    assertThat(saved.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(saved.getStripeCheckoutSessionId()).isNull();
    assertThat(saved.getPaidAt()).isNull();
    assertThat(eventCount(event.getId())).isZero();

    // The failed event must not be treated as processed.
    assertInvalid(event);
    assertThat(eventCount(event.getId())).isZero();
  }

  @Test
  void shouldFlagPaymentConfirmedAfterOrderCancellation()
    throws Exception {

    var order = orders.findById(orderId).orElseThrow();

    orderStatus.changeStatus(
      orderId,
      new ChangeOrderStatusRequest(
        "cancelled",
        order.getVersion()
      )
    );

    var paidEvent = event(
      "checkout.session.completed",
      "complete",
      "paid",
      2900,
      "pi_test_after_cancellation"
    );

    processor.process(paidEvent);
    processor.process(paidEvent);

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);

    assertThat(orders.findById(orderId).orElseThrow()
      .getStatus()).isEqualTo(OrderStatus.CANCELLED);

    assertThat(jdbc.queryForObject(
      """
      SELECT status
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attemptId
    )).isEqualTo("needs_review");

    assertThat(jdbc.queryForObject(
      """
      SELECT last_error_code
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attemptId
    )).isEqualTo("PAYMENT_AFTER_CANCELLATION");

    assertThat(eventCount(paidEvent.getId())).isEqualTo(1L);
  }

  @Test
  void shouldReopenCompletedExpirationTaskForLatePayment()
    throws Exception {

    var order = orders.findById(orderId).orElseThrow();

    orderStatus.changeStatus(
      orderId,
      new ChangeOrderStatusRequest(
        "cancelled",
        order.getVersion()
      )
    );

    processor.process(event(
      "checkout.session.expired",
      "expired",
      "unpaid",
      2900,
      null
    ));

    var claim = expirationQueue.claimNext(Instant.now())
      .orElseThrow();

    assertThat(expirationQueue.complete(
      claim,
      Instant.now()
    )).isTrue();

    processor.process(event(
      "checkout.session.async_payment_succeeded",
      "complete",
      "paid",
      2900,
      "pi_test_late_after_cancellation"
    ));

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);

    assertThat(jdbc.queryForObject(
      """
      SELECT status
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attemptId
    )).isEqualTo("needs_review");

    assertThat(jdbc.queryForObject(
      """
      SELECT last_error_code
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attemptId
    )).isEqualTo("PAYMENT_AFTER_CANCELLATION");
  }

  private void assertInvalid(Event event) {
    assertThatThrownBy(() -> processor.process(event))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode().value())
            .isEqualTo(400);
          assertThat(exception.getReason())
            .isEqualTo("STRIPE_WEBHOOK_DATA_INVALID");
        }
      );
  }

  private PaymentAttempt savedAttempt() {
    return attempts.findById(attemptId).orElseThrow();
  }

  private long eventCount(String eventId) {
    return jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM stripe_webhook_events
      WHERE event_id = ?
      """,
      Long.class,
      eventId
    );
  }

  private Event event(
    String type,
    String sessionStatus,
    String paymentStatus,
    long amount,
    String paymentIntent
  ) throws Exception {
    String eventId = "evt_" + UUID.randomUUID();
    eventIds.add(eventId);

    long timestamp = Instant.now().getEpochSecond();

    String intentJson = paymentIntent == null
      ? "null"
      : "\"" + paymentIntent + "\"";

    String payload = """
      {
        "id": "%s",
        "object": "event",
        "api_version": "%s",
        "created": %d,
        "livemode": false,
        "type": "%s",
        "data": {
          "object": {
            "id": "%s",
            "object": "checkout.session",
            "livemode": false,
            "mode": "payment",
            "status": "%s",
            "payment_status": "%s",
            "client_reference_id": "%s",
            "amount_total": %d,
            "currency": "pln",
            "expires_at": %d,
            "payment_intent": %s,
            "metadata": {
              "order_id": "%s",
              "payment_attempt_id": "%s"
            }
          }
        }
      }
      """.formatted(
      eventId,
      Stripe.API_VERSION,
      timestamp,
      type,
      sessionId,
      sessionStatus,
      paymentStatus,
      attemptId,
      amount,
      expiresAt.getEpochSecond(),
      intentJson,
      orderId,
      attemptId
    );

    var mac = Mac.getInstance("HmacSHA256");

    mac.init(new SecretKeySpec(
      SECRET.getBytes(StandardCharsets.UTF_8),
      "HmacSHA256"
    ));

    byte[] digest = mac.doFinal(
      (timestamp + "." + payload)
        .getBytes(StandardCharsets.UTF_8)
    );

    String signature = "t=" + timestamp
      + ",v1="
      + HexFormat.of().formatHex(digest);

    return new StripeWebhookVerifier(SECRET)
      .verify(payload, signature);
  }
}
