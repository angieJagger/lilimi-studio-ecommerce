package pl.lilimi.payment.cancellation;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.service.checkout.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.admin.ChangeOrderStatusRequest;
import pl.lilimi.order.application.admin.AdminOrderStatusService;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=true",
  "lilimi.payments.stripe.api-key=sk_test_placeholder_for_mocked_tests",
  "lilimi.payments.stripe.expiration.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
class CheckoutExpirationIntegrationTest {

  @Autowired
  private CheckoutExpirationProcessor processor;

  @Autowired
  private AdminOrderStatusService orderStatus;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private OrderItemRepository items;

  @Autowired
  private PaymentAttemptRepository payments;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private SessionService sessions;

  private UUID orderId;
  private UUID attemptId;
  private String sessionId;
  private Instant expiresAt;

  @BeforeEach
  void createOrder() {
    var order = orders.saveAndFlush(new Order(
      "en",
      new OrderContact(
        "Test Customer",
        "expiration@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    ));

    orderId = order.getId();
    sessionId = "cs_test_" + UUID.randomUUID();
    expiresAt = Instant.now().plusSeconds(1800);

    items.saveAndFlush(OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Saved pattern",
      2900
    ));
  }

  @AfterEach
  void cleanUp() {
    if (orderId == null) {
      return;
    }

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
      "DELETE FROM order_items WHERE order_id = ?",
      orderId
    );

    jdbc.update(
      "DELETE FROM orders WHERE id = ?",
      orderId
    );
  }

  @Test
  void shouldExpireSessionAfterOrderCancellation()
    throws StripeException {

    saveAttempt(true);
    cancelOrder();

    when(sessions.retrieve(sessionId))
      .thenAnswer(invocation -> {
        assertOutsideTransaction();

        assertThat(orders.findById(orderId).orElseThrow()
          .getStatus()).isEqualTo(OrderStatus.CANCELLED);

        return session("open", "unpaid");
      });

    when(sessions.expire(
      eq(sessionId),
      any(RequestOptions.class)
    )).thenAnswer(invocation -> {
      assertOutsideTransaction();
      return session("expired", "unpaid");
    });

    assertThat(processor.processNext()).isTrue();

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.EXPIRED);
    assertThat(taskStatus()).isEqualTo("completed");
    assertThat(orders.findById(orderId).orElseThrow()
      .getStatus()).isEqualTo(OrderStatus.CANCELLED);

    assertThat(processor.processNext()).isFalse();
  }

  @Test
  void shouldRecoverCreatedSessionUsingOriginalIdempotencyKey()
    throws StripeException {

    saveAttempt(false);
    cancelOrder();

    when(sessions.create(
      any(SessionCreateParams.class),
      any(RequestOptions.class)
    )).thenAnswer(invocation -> {
      assertOutsideTransaction();

      RequestOptions options = invocation.getArgument(1);

      assertThat(options.getIdempotencyKey())
        .isEqualTo("lilimi-checkout-" + attemptId);
      assertThat(savedAttempt().getStatus())
        .isEqualTo(PaymentAttemptStatus.CREATED);

      return session("open", "unpaid");
    });

    when(sessions.expire(
      eq(sessionId),
      any(RequestOptions.class)
    )).thenAnswer(invocation -> {
      assertOutsideTransaction();

      // The recovered session must be committed before
      // the next network request.
      assertThat(savedAttempt().getStatus())
        .isEqualTo(PaymentAttemptStatus.PENDING);
      assertThat(savedAttempt().getStripeCheckoutSessionId())
        .isEqualTo(sessionId);

      return session("expired", "unpaid");
    });

    assertThat(processor.processNext()).isTrue();

    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.EXPIRED);
    assertThat(taskStatus()).isEqualTo("completed");
  }

  @Test
  void shouldRequireReviewWhenStripeAlreadyCompletedPayment()
    throws StripeException {

    saveAttempt(true);
    cancelOrder();

    when(sessions.retrieve(sessionId))
      .thenReturn(session("complete", "paid"));

    assertThat(processor.processNext()).isTrue();

    assertThat(taskStatus()).isEqualTo("needs_review");
    assertThat(jdbc.queryForObject(
      """
      SELECT last_error_code
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attemptId
    )).isEqualTo("PAYMENT_ALREADY_COMPLETED");

    // Retrieval does not replace webhook confirmation.
    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);

    verify(sessions, never()).expire(
      any(String.class),
      any(RequestOptions.class)
    );
  }

  @Test
  void shouldScheduleRetryAfterProviderFailure()
    throws StripeException {

    saveAttempt(true);
    cancelOrder();

    when(sessions.retrieve(sessionId))
      .thenThrow(mock(StripeException.class));

    assertThat(processor.processNext()).isTrue();

    assertThat(taskStatus()).isEqualTo("pending");
    assertThat(savedAttempt().getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);

    assertThat(jdbc.queryForObject(
      """
      SELECT attempts
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      Integer.class,
      attemptId
    )).isEqualTo(1);

    assertThat(jdbc.queryForObject(
      """
      SELECT lease_token IS NULL AND lease_until IS NULL
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      Boolean.class,
      attemptId
    )).isTrue();

    assertThat(jdbc.queryForObject(
      """
      SELECT last_error_code
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attemptId
    )).isEqualTo("STRIPE_UNAVAILABLE");

    assertThat(processor.processNext()).isFalse();
  }

  private void saveAttempt(boolean attachSession) {
    var attempt = new PaymentAttempt(
      orderId,
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      Instant.now().minusSeconds(60)
    );

    if (attachSession) {
      attempt.attachCheckoutSession(
        sessionId,
        expiresAt
      );
    }

    attemptId = payments.saveAndFlush(attempt).getId();
  }

  private void cancelOrder() {
    var order = orders.findById(orderId).orElseThrow();

    orderStatus.changeStatus(
      orderId,
      new ChangeOrderStatusRequest(
        "cancelled",
        order.getVersion()
      )
    );
  }

  private PaymentAttempt savedAttempt() {
    return payments.findById(attemptId).orElseThrow();
  }

  private String taskStatus() {
    return jdbc.queryForObject(
      """
      SELECT status
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attemptId
    );
  }

  private static void assertOutsideTransaction() {
    assertThat(
      TransactionSynchronizationManager
        .isActualTransactionActive()
    ).isFalse();
  }

  private Session session(
    String status,
    String paymentStatus
  ) {
    var session = new Session();

    session.setId(sessionId);
    session.setLivemode(false);
    session.setMode("payment");
    session.setStatus(status);
    session.setPaymentStatus(paymentStatus);
    session.setClientReferenceId(attemptId.toString());
    session.setAmountTotal(2900L);
    session.setCurrency("pln");
    session.setExpiresAt(expiresAt.getEpochSecond());

    session.setMetadata(Map.of(
      "order_id", orderId.toString(),
      "payment_attempt_id", attemptId.toString()
    ));

    return session;
  }
}
