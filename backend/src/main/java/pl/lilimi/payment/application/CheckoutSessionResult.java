package pl.lilimi.payment.application;

import java.util.UUID;

public record CheckoutSessionResult(
  UUID paymentAttemptId,
  String status,
  String checkoutUrl
) {
}
