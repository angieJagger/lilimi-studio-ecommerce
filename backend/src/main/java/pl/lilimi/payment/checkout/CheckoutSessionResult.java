package pl.lilimi.payment.checkout;

import java.util.UUID;

public record CheckoutSessionResult(
  UUID paymentAttemptId,
  String status,
  String checkoutUrl
) {
}
