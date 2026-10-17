package pl.lilimi.payment.stripe;

import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.payment.domain.PaymentAttempt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeCheckoutParamsFactoryTest {

  private static final Instant NOW =
    Instant.parse("2026-10-16T10:00:00Z");

  private final StripeCheckoutParamsFactory factory =
    new StripeCheckoutParamsFactory(
      "http://localhost:4200/"
    );

  @Test
  void shouldPreparePolishDigitalCheckoutFromSavedDetails() {
    var order = order("pl", OrderDelivery.digital(), 2900, 0);
    var attempt = attempt(order, 2900, false);

    var item = OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Leśny smok",
      2900
    );

    var params = factory.create(
      attempt,
      order,
      List.of(item)
    );

    assertThat(params.getMode())
      .isEqualTo(SessionCreateParams.Mode.PAYMENT);

    assertThat(params.getUiMode())
      .isEqualTo(SessionCreateParams.UiMode.HOSTED_PAGE);

    assertThat(params.getLocale())
      .isEqualTo(SessionCreateParams.Locale.PL);

    assertThat(params.getCustomerEmail())
      .isEqualTo("customer@example.com");

    assertThat(params.getClientReferenceId())
      .isEqualTo(attempt.getId().toString());

    assertThat(params.getMetadata())
      .containsEntry(
        "payment_attempt_id",
        attempt.getId().toString()
      )
      .containsEntry("order_id", order.getId().toString());

    assertThat(params.getPaymentIntentData().getMetadata())
      .containsEntry(
        "payment_attempt_id",
        attempt.getId().toString()
      );

    assertThat(params.getLineItems()).hasSize(1);

    var line = params.getLineItems().get(0);

    assertThat(line.getQuantity()).isEqualTo(1L);
    assertThat(line.getPriceData().getUnitAmount())
      .isEqualTo(2900L);
    assertThat(line.getPriceData().getCurrency())
      .isEqualTo("pln");
    assertThat(line.getPriceData().getProductData().getName())
      .isEqualTo("Leśny smok");

    assertThat(params.getShippingOptions()).isNull();

    assertThat(params.getAllowPromotionCodes()).isFalse();
    assertThat(params.getAutomaticTax().getEnabled()).isFalse();
    assertThat(params.getAdaptivePricing().getEnabled()).isFalse();

    assertThat(params.getSuccessUrl()).isEqualTo(
      "http://localhost:4200/pl/checkout/payment-result"
        + "?attempt=" + attempt.getId()
        + "&session_id={CHECKOUT_SESSION_ID}"
    );

    assertThat(params.getCancelUrl()).isEqualTo(
      "http://localhost:4200/pl/checkout/payment-result"
        + "?attempt=" + attempt.getId()
        + "&cancelled=true"
    );
  }

  @Test
  void shouldPrepareEnglishCheckoutWithSeparateDeliveryCost() {
    var delivery = OrderDelivery.courier(
      "dhl-courier",
      "Testowa 12",
      null,
      "00-001",
      "Warszawa",
      "PL"
    );

    var order = order("en", delivery, 33800, 1200);
    var attempt = attempt(order, 35000, false);

    var item = OrderItem.sweatshirt(
      order,
      1,
      "sweatshirt-001",
      "Embroidered sweatshirt",
      "pattern-001",
      "Forest dragon",
      "women",
      "M",
      "black",
      "small-front",
      2,
      16900
    );

    var params = factory.create(
      attempt,
      order,
      List.of(item)
    );

    assertThat(params.getLocale())
      .isEqualTo(SessionCreateParams.Locale.EN);

    assertThat(params.getLineItems()).hasSize(1);
    assertThat(params.getLineItems().get(0).getQuantity())
      .isEqualTo(2L);

    assertThat(
      params.getLineItems().get(0)
        .getPriceData().getUnitAmount()
    ).isEqualTo(16900L);

    assertThat(params.getShippingOptions()).hasSize(1);

    var shipping = params.getShippingOptions().get(0)
      .getShippingRateData();

    assertThat(shipping.getDisplayName())
      .isEqualTo("Courier delivery");

    assertThat(shipping.getFixedAmount().getAmount())
      .isEqualTo(1200L);

    assertThat(shipping.getFixedAmount().getCurrency())
      .isEqualTo("pln");

    assertThat(params.getSuccessUrl())
      .startsWith(
        "http://localhost:4200/en/checkout/payment-result"
      );
  }

  @Test
  void shouldRejectAttemptWithDifferentAmount() {
    var order = order("pl", OrderDelivery.digital(), 2900, 0);
    var attempt = attempt(order, 1900, false);

    var item = OrderItem.digital(
      order, 1, "pattern-001", "Leśny smok", 2900
    );

    assertThatThrownBy(() ->
      factory.create(attempt, order, List.of(item))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Payment attempt does not match the order");
  }

  @Test
  void shouldRejectItemBelongingToAnotherOrder() {
    var order = order("pl", OrderDelivery.digital(), 2900, 0);
    var otherOrder = order(
      "pl", OrderDelivery.digital(), 2900, 0
    );

    var attempt = attempt(order, 2900, false);

    var item = OrderItem.digital(
      otherOrder, 1, "pattern-001", "Leśny smok", 2900
    );

    assertThatThrownBy(() ->
      factory.create(attempt, order, List.of(item))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Item belongs to a different order");
  }

  @Test
  void shouldRejectItemsThatDoNotMatchSavedSubtotal() {
    var order = order("pl", OrderDelivery.digital(), 2900, 0);
    var attempt = attempt(order, 2900, false);

    var item = OrderItem.digital(
      order, 1, "pattern-001", "Leśny smok", 1900
    );

    assertThatThrownBy(() ->
      factory.create(attempt, order, List.of(item))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
        "Saved item amounts do not match the payment"
      );
  }

  @Test
  void shouldRejectLiveModeAttempt() {
    var order = order("pl", OrderDelivery.digital(), 2900, 0);
    var attempt = attempt(order, 2900, true);

    var item = OrderItem.digital(
      order, 1, "pattern-001", "Leśny smok", 2900
    );

    assertThatThrownBy(() ->
      factory.create(attempt, order, List.of(item))
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectAttemptWithAlreadyAttachedSession() {
    var order = order("pl", OrderDelivery.digital(), 2900, 0);
    var attempt = attempt(order, 2900, false);

    attempt.attachCheckoutSession(
      "cs_test_existing",
      NOW.plusSeconds(1800)
    );

    var item = OrderItem.digital(
      order, 1, "pattern-001", "Leśny smok", 2900
    );

    assertThatThrownBy(() ->
      factory.create(attempt, order, List.of(item))
    ).isInstanceOf(IllegalArgumentException.class);
  }

  private Order order(
    String language,
    OrderDelivery delivery,
    long subtotal,
    long deliveryPrice
  ) {
    var order = new Order(
      language,
      new OrderContact(
        "Anna Kowalska",
        "customer@example.com",
        null
      ),
      delivery,
      subtotal,
      deliveryPrice
    );

    ReflectionTestUtils.setField(
      order,
      "id",
      UUID.randomUUID()
    );

    return order;
  }

  private PaymentAttempt attempt(
    Order order,
    long amount,
    boolean livemode
  ) {
    return new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      amount,
      "PLN",
      livemode,
      NOW
    );
  }
}
