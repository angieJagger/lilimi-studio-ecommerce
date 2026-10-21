package pl.lilimi.order.api.admin;

import pl.lilimi.order.domain.Order;
import pl.lilimi.payment.cancellation.CheckoutExpirationSummary;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderSummaryResponse(
  UUID id,
  Instant createdAt,
  String status,
  String customerFullName,
  String customerEmail,
  String currency,
  long totalInGrosz,
  CheckoutExpirationSummary paymentCancellation
) {

  public static AdminOrderSummaryResponse from(
    Order order,
    CheckoutExpirationSummary paymentCancellation
  ) {
    return new AdminOrderSummaryResponse(
      order.getId(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getContact().getFullName(),
      order.getContact().getEmail(),
      order.getCurrency(),
      order.getTotalInGrosz(),
      paymentCancellation
    );
  }
}
