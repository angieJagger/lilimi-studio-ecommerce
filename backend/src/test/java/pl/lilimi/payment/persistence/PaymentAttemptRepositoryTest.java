package pl.lilimi.payment.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class PaymentAttemptRepositoryTest {

  private static final Instant CREATED_AT =
    Instant.parse("2026-10-16T10:00:00Z");

  private static final Instant EXPIRES_AT =
    CREATED_AT.plusSeconds(1800);

  private static final Instant PAID_AT =
    CREATED_AT.plusSeconds(60);

  @Autowired
  private PaymentAttemptRepository payments;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldPersistAndReadConfirmedPayment() {
    var order = createOrder();
    UUID key = UUID.randomUUID();

    var attempt = newAttempt(order.getId(), key);
    attempt.attachCheckoutSession(
      "cs_test_saved",
      EXPIRES_AT
    );

    attempt.confirmPayment(
      "cs_test_saved",
      "pi_saved",
      2900,
      "pln",
      false,
      PAID_AT
    );

    UUID attemptId = payments.saveAndFlush(attempt).getId();

    entityManager.clear();

    var restored = payments.findByIdempotencyKey(key)
      .orElseThrow();

    assertThat(restored.getId()).isEqualTo(attemptId);
    assertThat(restored.getOrderId()).isEqualTo(order.getId());
    assertThat(restored.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(restored.getAmountInGrosz()).isEqualTo(2900);
    assertThat(restored.getCurrency()).isEqualTo("PLN");
    assertThat(restored.isLivemode()).isFalse();
    assertThat(restored.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(restored.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(restored.getPaidAt()).isEqualTo(PAID_AT);
    assertThat(restored.getStripePaymentIntentId())
      .isEqualTo("pi_saved");

    assertThat(
      payments.findByStripeCheckoutSessionId("cs_test_saved")
        .orElseThrow()
        .getId()
    ).isEqualTo(attemptId);
  }

  @Test
  void shouldRejectSecondActiveAttemptForSameOrder() {
    var order = createOrder();

    payments.saveAndFlush(
      newAttempt(order.getId(), UUID.randomUUID())
    );

    var second = newAttempt(
      order.getId(),
      UUID.randomUUID()
    );

    assertThatThrownBy(() -> payments.saveAndFlush(second))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shouldAllowNewAttemptAfterPreviousSessionExpired() {
    var order = createOrder();

    var first = newAttempt(
      order.getId(),
      UUID.randomUUID()
    );

    first.attachCheckoutSession(
      "cs_test_expired",
      EXPIRES_AT
    );
    first.markExpired("cs_test_expired");

    payments.saveAndFlush(first);

    var second = payments.saveAndFlush(
      newAttempt(order.getId(), UUID.randomUUID())
    );

    entityManager.clear();

    var active = payments.findByOrderIdAndStatusIn(
      order.getId(),
      List.of(
        PaymentAttemptStatus.CREATED,
        PaymentAttemptStatus.PENDING
      )
    ).orElseThrow();

    assertThat(active.getId()).isEqualTo(second.getId());
  }

  @Test
  void shouldAllowNewAttemptAfterDefinitiveCreationFailure() {
    var order = createOrder();

    var first = newAttempt(
      order.getId(),
      UUID.randomUUID()
    );

    first.markCreationFailed();
    payments.saveAndFlush(first);

    var second = payments.saveAndFlush(
      newAttempt(order.getId(), UUID.randomUUID())
    );

    assertThat(second.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(second.getId()).isNotEqualTo(first.getId());
  }

  @Test
  void shouldRejectReusedIdempotencyKeyAcrossOrders() {
    UUID key = UUID.randomUUID();

    var firstOrder = createOrder();
    var secondOrder = createOrder();

    payments.saveAndFlush(
      newAttempt(firstOrder.getId(), key)
    );

    var second = newAttempt(secondOrder.getId(), key);

    assertThatThrownBy(() -> payments.saveAndFlush(second))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shouldRejectCheckoutSessionAssignedToTwoAttempts() {
    var firstOrder = createOrder();
    var secondOrder = createOrder();

    var first = newAttempt(
      firstOrder.getId(),
      UUID.randomUUID()
    );

    first.attachCheckoutSession(
      "cs_test_shared",
      EXPIRES_AT
    );

    payments.saveAndFlush(first);

    var second = newAttempt(
      secondOrder.getId(),
      UUID.randomUUID()
    );

    second.attachCheckoutSession(
      "cs_test_shared",
      EXPIRES_AT
    );

    assertThatThrownBy(() -> payments.saveAndFlush(second))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shouldRejectPaymentIntentAssignedToTwoAttempts() {
    var firstOrder = createOrder();
    var secondOrder = createOrder();

    var first = newAttempt(
      firstOrder.getId(),
      UUID.randomUUID()
    );

    first.attachCheckoutSession(
      "cs_test_first",
      EXPIRES_AT
    );

    first.confirmPayment(
      "cs_test_first",
      "pi_shared",
      2900,
      "PLN",
      false,
      PAID_AT
    );

    payments.saveAndFlush(first);

    var second = newAttempt(
      secondOrder.getId(),
      UUID.randomUUID()
    );

    second.attachCheckoutSession(
      "cs_test_second",
      EXPIRES_AT
    );

    second.confirmPayment(
      "cs_test_second",
      "pi_shared",
      2900,
      "PLN",
      false,
      PAID_AT
    );

    assertThatThrownBy(() -> payments.saveAndFlush(second))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Order createOrder() {
    return orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        "payment-test@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    ));
  }

  private PaymentAttempt newAttempt(
    UUID orderId,
    UUID key
  ) {
    return new PaymentAttempt(
      orderId,
      key,
      2900,
      "PLN",
      false,
      CREATED_AT
    );
  }
}
