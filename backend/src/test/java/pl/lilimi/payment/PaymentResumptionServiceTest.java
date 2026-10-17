package pl.lilimi.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.application.PaymentAttemptService;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class PaymentResumptionServiceTest {

  @Autowired
  private PaymentAttemptService service;

  @Autowired
  private PaymentAttemptRepository attempts;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldReuseCreatedAttemptWithDifferentRequestKey() {
    var order = createOrder();
    var original = saveCreatedAttempt(order);
    var retryKey = UUID.randomUUID();

    var resumed = service.prepareForResumption(
      order.getId(),
      retryKey
    );

    assertThat(resumed.getId()).isEqualTo(original.getId());
    assertThat(resumed.getIdempotencyKey())
      .isEqualTo(original.getIdempotencyKey());
    assertThat(resumed.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(attempts.findByIdempotencyKey(retryKey))
      .isEmpty();
    assertThat(attemptCount(order)).isEqualTo(1L);
  }

  @Test
  void shouldReusePendingAttemptAndItsStripeSession() {
    var order = createOrder();
    var original = savePendingAttempt(order);

    var resumed = service.prepareForResumption(
      order.getId(),
      UUID.randomUUID()
    );

    assertThat(resumed.getId()).isEqualTo(original.getId());
    assertThat(resumed.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
    assertThat(resumed.getStripeCheckoutSessionId())
      .isEqualTo(original.getStripeCheckoutSessionId());
    assertThat(attemptCount(order)).isEqualTo(1L);
  }

  @Test
  void shouldCreateNewAttemptAfterPreviousAttemptExpired() {
    var order = createOrder();
    var original = savePendingAttempt(order);

    original.markExpired(
      original.getStripeCheckoutSessionId()
    );
    attempts.saveAndFlush(original);

    var retryKey = UUID.randomUUID();

    var resumed = service.prepareForResumption(
      order.getId(),
      retryKey
    );

    assertThat(resumed.getId())
      .isNotEqualTo(original.getId());
    assertThat(resumed.getIdempotencyKey())
      .isEqualTo(retryKey);
    assertThat(resumed.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(attemptCount(order)).isEqualTo(2L);

    var repeated = service.prepareForResumption(
      order.getId(),
      retryKey
    );

    assertThat(repeated.getId()).isEqualTo(resumed.getId());
    assertThat(attemptCount(order)).isEqualTo(2L);
  }

  @Test
  void shouldCreateNewAttemptAfterCreationFailure() {
    var order = createOrder();
    var original = saveCreatedAttempt(order);

    original.markCreationFailed();
    attempts.saveAndFlush(original);

    var resumed = service.prepareForResumption(
      order.getId(),
      UUID.randomUUID()
    );

    assertThat(resumed.getId())
      .isNotEqualTo(original.getId());
    assertThat(resumed.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(attemptCount(order)).isEqualTo(2L);
  }

  @Test
  void shouldRejectNewAttemptForPaidOrder() {
    var order = createOrder();
    var paid = savePendingAttempt(order);

    paid.confirmPayment(
      paid.getStripeCheckoutSessionId(),
      "pi_test_" + UUID.randomUUID(),
      2900,
      "PLN",
      false,
      Instant.now()
    );
    attempts.saveAndFlush(paid);

    assertConflict(
      order,
      UUID.randomUUID(),
      "ORDER_ALREADY_PAID"
    );

    assertThat(attemptCount(order)).isEqualTo(1L);
  }

  @Test
  void shouldRejectResumingCancelledOrderWithNewKey() {
    var order = createOrder();
    savePendingAttempt(order);

    order.changeStatus(OrderStatus.CANCELLED);
    orders.saveAndFlush(order);

    assertConflict(
      order,
      UUID.randomUUID(),
      "ORDER_NOT_PAYABLE"
    );

    assertThat(attemptCount(order)).isEqualTo(1L);
  }

  @Test
  void shouldRejectKeyBelongingToAnotherOrder() {
    var firstOrder = createOrder();
    var secondOrder = createOrder();
    var original = saveCreatedAttempt(firstOrder);

    assertConflict(
      secondOrder,
      original.getIdempotencyKey(),
      "PAYMENT_IDEMPOTENCY_CONFLICT"
    );

    assertThat(attemptCount(firstOrder)).isEqualTo(1L);
    assertThat(attemptCount(secondOrder)).isZero();
  }

  private void assertConflict(
    Order order,
    UUID key,
    String reason
  ) {
    assertThatThrownBy(() ->
      service.prepareForResumption(order.getId(), key)
    ).isInstanceOfSatisfying(
      ResponseStatusException.class,
      exception -> {
        assertThat(exception.getStatusCode().value())
          .isEqualTo(409);
        assertThat(exception.getReason()).isEqualTo(reason);
      }
    );
  }

  private Order createOrder() {
    return orders.saveAndFlush(new Order(
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
  }

  private PaymentAttempt saveCreatedAttempt(Order order) {
    return attempts.saveAndFlush(new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      Instant.now().minusSeconds(60)
    ));
  }

  private PaymentAttempt savePendingAttempt(Order order) {
    var attempt = new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      Instant.now().minusSeconds(60)
    );

    attempt.attachCheckoutSession(
      "cs_test_" + UUID.randomUUID(),
      Instant.now().plusSeconds(1800)
    );

    return attempts.saveAndFlush(attempt);
  }

  private long attemptCount(Order order) {
    return jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM order_payment_attempts
      WHERE order_id = ?
      """,
      Long.class,
      order.getId()
    );
  }
}
