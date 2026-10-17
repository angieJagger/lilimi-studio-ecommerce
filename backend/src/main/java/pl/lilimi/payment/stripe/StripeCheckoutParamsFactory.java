package pl.lilimi.payment.stripe;

import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class StripeCheckoutParamsFactory {

  private final String publicBaseUrl;

  public StripeCheckoutParamsFactory(
    @Value("${lilimi.public-base-url:https://lilimistudio.com}")
    String publicBaseUrl
  ) {
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  public SessionCreateParams create(
    PaymentAttempt attempt,
    Order order,
    List<OrderItem> items
  ) {
    Objects.requireNonNull(attempt, "Payment attempt is required");
    Objects.requireNonNull(order, "Order is required");
    Objects.requireNonNull(items, "Order items are required");

    if (
      attempt.isLivemode()
        || attempt.getStatus() != PaymentAttemptStatus.CREATED
    ) {
      throw new IllegalArgumentException(
        "An uninitialized test payment attempt is required"
      );
    }

    if (
      !attempt.getOrderId().equals(order.getId())
        || attempt.getAmountInGrosz() != order.getTotalInGrosz()
        || !attempt.getCurrency().equals(order.getCurrency())
    ) {
      throw new IllegalArgumentException(
        "Payment attempt does not match the order"
      );
    }

    if (items.isEmpty() || items.size() > 100) {
      throw new IllegalArgumentException(
        "Checkout requires between 1 and 100 items"
      );
    }

    long subtotal = 0;

    for (var item : items) {
      if (!Objects.equals(
        item.getOrder().getId(),
        order.getId()
      )) {
        throw new IllegalArgumentException(
          "Item belongs to a different order"
        );
      }

      long lineTotal = Math.multiplyExact(
        item.getUnitPriceInGrosz(),
        item.getQuantity()
      );

      if (lineTotal != item.getLineTotalInGrosz()) {
        throw new IllegalArgumentException(
          "Invalid saved item total"
        );
      }

      subtotal = Math.addExact(subtotal, lineTotal);
    }

    if (
      subtotal != order.getSubtotalInGrosz()
        || Math.addExact(
        subtotal,
        order.getDeliveryPriceInGrosz()
      ) != attempt.getAmountInGrosz()
    ) {
      throw new IllegalArgumentException(
        "Saved item amounts do not match the payment"
      );
    }

    boolean polish = "pl".equals(order.getLanguage());
    String language = polish ? "pl" : "en";
    String currency = order.getCurrency()
      .toLowerCase(Locale.ROOT);

    String resultUrl = publicBaseUrl
      + "/" + language
      + "/checkout/payment-result"
      + "?attempt=" + attempt.getId();

    var builder = SessionCreateParams.builder()
      .setMode(SessionCreateParams.Mode.PAYMENT)
      .setUiMode(SessionCreateParams.UiMode.HOSTED_PAGE)
      .setLocale(
        polish
          ? SessionCreateParams.Locale.PL
          : SessionCreateParams.Locale.EN
      )
      .setCustomerEmail(order.getContact().getEmail())
      .setClientReferenceId(attempt.getId().toString())
      .putMetadata("payment_attempt_id", attempt.getId().toString())
      .putMetadata("order_id", order.getId().toString())
      .setPaymentIntentData(
        SessionCreateParams.PaymentIntentData.builder()
          .putMetadata(
            "payment_attempt_id",
            attempt.getId().toString()
          )
          .putMetadata("order_id", order.getId().toString())
          .build()
      )
      .setSuccessUrl(
        resultUrl + "&session_id={CHECKOUT_SESSION_ID}"
      )
      .setCancelUrl(resultUrl + "&cancelled=true")
      .setAllowPromotionCodes(false)
      .setAutomaticTax(
        SessionCreateParams.AutomaticTax.builder()
          .setEnabled(false)
          .build()
      )
      .setAdaptivePricing(
        SessionCreateParams.AdaptivePricing.builder()
          .setEnabled(false)
          .build()
      );

    var sortedItems = items.stream()
      .sorted(Comparator.comparingInt(OrderItem::getPosition))
      .toList();

    for (var item : sortedItems) {
      builder.addLineItem(
        SessionCreateParams.LineItem.builder()
          .setQuantity((long) item.getQuantity())
          .setPriceData(
            SessionCreateParams.LineItem.PriceData.builder()
              .setCurrency(currency)
              .setUnitAmount(item.getUnitPriceInGrosz())
              .setProductData(
                SessionCreateParams.LineItem.PriceData.ProductData
                  .builder()
                  .setName(item.getProductName())
                  .build()
              )
              .build()
          )
          .build()
      );
    }

    if ("courier".equals(order.getDelivery().getKind())) {
      builder.addShippingOption(
        SessionCreateParams.ShippingOption.builder()
          .setShippingRateData(
            SessionCreateParams.ShippingOption.ShippingRateData
              .builder()
              .setType(
                SessionCreateParams.ShippingOption.ShippingRateData
                  .Type.FIXED_AMOUNT
              )
              .setDisplayName(
                polish ? "Dostawa kurierska" : "Courier delivery"
              )
              .setFixedAmount(
                SessionCreateParams.ShippingOption.ShippingRateData
                  .FixedAmount.builder()
                  .setAmount(order.getDeliveryPriceInGrosz())
                  .setCurrency(currency)
                  .build()
              )
              .build()
          )
          .build()
      );
    }

    return builder.build();
  }
}
