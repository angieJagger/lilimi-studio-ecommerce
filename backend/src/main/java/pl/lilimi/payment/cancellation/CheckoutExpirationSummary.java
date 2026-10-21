package pl.lilimi.payment.cancellation;

public record CheckoutExpirationSummary(
  String status,
  String reasonCode
) {
}
