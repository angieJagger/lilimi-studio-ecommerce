package pl.lilimi.payment.application;

public record OrderPaymentSummary(
  String status,
  boolean canPay
) {
}
