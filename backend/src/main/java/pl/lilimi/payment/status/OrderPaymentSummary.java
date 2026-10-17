package pl.lilimi.payment.status;

public record OrderPaymentSummary(
  String status,
  boolean canPay
) {
}
