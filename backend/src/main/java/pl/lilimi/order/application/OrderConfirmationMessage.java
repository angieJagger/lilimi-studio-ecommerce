package pl.lilimi.order.application;

public record OrderConfirmationMessage(
  String recipient,
  String subject,
  String text
) {
}
