package pl.lilimi.payment.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderPaymentSummaryServiceTest {

  private static final Instant CREATED_AT =
    Instant.parse("2026-10-17T10:00:00Z");

  @Autowired
  private OrderRepository orders;

  @Autowired
  private PaymentAttemptRepository attempts;

  @Autowired
  private OrderPaymentSummaryService disabledService;

  @Test
  void shouldAllowPaymentWhenNoAttemptExists() {
    var order = createOrder();

    assertThat(summary(order))
      .isEqualTo(new OrderPaymentSummary("unpaid", true));
  }

  @Test
  void shouldDisablePaymentWhenStripeIsDisabled() {
    var order = createOrder();

    var result = disabledService.summarize(List.of(order));

    assertThat(result.get(order.getId()))
      .isEqualTo(new OrderPaymentSummary("unpaid", false));
  }

  @Test
  void shouldPreferActiveAttemptOverNewerFailedAttempt() {
    var order = createOrder();

    var pending = newAttempt(order, CREATED_AT);
    pending.attachCheckoutSession(
      sessionId(),
      CREATED_AT.plusSeconds(1800)
    );
    attempts.saveAndFlush(pending);

    var failed = newAttempt(
      order,
      CREATED_AT.plusSeconds(10)
    );
    failed.markCreationFailed();
    attempts.saveAndFlush(failed);

    assertThat(summary(order))
      .isEqualTo(new OrderPaymentSummary("pending", true));
  }

  @Test
  void shouldPreferConfirmedPaymentOverAnotherPendingAttempt() {
    var order = createOrder();

    var paid = newAttempt(order, CREATED_AT);
    String sessionId = sessionId();

    paid.attachCheckoutSession(
      sessionId,
      CREATED_AT.plusSeconds(1800)
    );

    paid.confirmPayment(
      sessionId,
      "pi_test_" + UUID.randomUUID(),
      2900,
      "PLN",
      false,
      CREATED_AT.plusSeconds(30)
    );

    attempts.saveAndFlush(paid);

    var pending = newAttempt(
      order,
      CREATED_AT.plusSeconds(60)
    );
    pending.attachCheckoutSession(
      sessionId(),
      CREATED_AT.plusSeconds(1900)
    );
    attempts.saveAndFlush(pending);

    assertThat(summary(order))
      .isEqualTo(new OrderPaymentSummary("succeeded", false));
  }

  @Test
  void shouldChooseLatestClosedAttemptWhenNoneIsActiveOrPaid() {
    var order = createOrder();

    var expired = newAttempt(order, CREATED_AT);
    String sessionId = sessionId();

    expired.attachCheckoutSession(
      sessionId,
      CREATED_AT.plusSeconds(1800)
    );
    expired.markExpired(sessionId);
    attempts.saveAndFlush(expired);

    var failed = newAttempt(
      order,
      CREATED_AT.plusSeconds(1900)
    );
    failed.markCreationFailed();
    attempts.saveAndFlush(failed);

    assertThat(summary(order))
      .isEqualTo(new OrderPaymentSummary("failed", true));
  }

  @Test
  void shouldNotAllowPaymentForCancelledOrder() {
    var order = createOrder();

    order.changeStatus(OrderStatus.CANCELLED);
    orders.saveAndFlush(order);

    assertThat(summary(order))
      .isEqualTo(new OrderPaymentSummary("unpaid", false));
  }

  @Test
  void shouldReturnSeparateSummariesForMultipleOrders() {
    var unpaidOrder = createOrder();
    var paidOrder = createOrder();

    var paid = newAttempt(paidOrder, CREATED_AT);
    String sessionId = sessionId();

    paid.attachCheckoutSession(
      sessionId,
      CREATED_AT.plusSeconds(1800)
    );

    paid.confirmPayment(
      sessionId,
      "pi_test_" + UUID.randomUUID(),
      2900,
      "PLN",
      false,
      CREATED_AT.plusSeconds(30)
    );

    attempts.saveAndFlush(paid);

    var service = new OrderPaymentSummaryService(
      attempts,
      true
    );

    var result = service.summarize(
      List.of(unpaidOrder, paidOrder)
    );

    assertThat(result).hasSize(2);
    assertThat(result.get(unpaidOrder.getId()))
      .isEqualTo(new OrderPaymentSummary("unpaid", true));
    assertThat(result.get(paidOrder.getId()))
      .isEqualTo(new OrderPaymentSummary("succeeded", false));
  }

  private OrderPaymentSummary summary(Order order) {
    var service = new OrderPaymentSummaryService(
      attempts,
      true
    );

    return service.summarize(List.of(order))
      .get(order.getId());
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

  private PaymentAttempt newAttempt(
    Order order,
    Instant createdAt
  ) {
    return new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      createdAt
    );
  }

  private static String sessionId() {
    return "cs_test_" + UUID.randomUUID();
  }
}
