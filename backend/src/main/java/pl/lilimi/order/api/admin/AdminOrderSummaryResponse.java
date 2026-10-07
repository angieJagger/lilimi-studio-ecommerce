package pl.lilimi.order.api.admin;

import pl.lilimi.order.domain.Order;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderSummaryResponse(
  UUID id,
  Instant createdAt,
  String status,
  String customerFullName,
  String customerEmail,
  String currency,
  long totalInGrosz
) {

  public static AdminOrderSummaryResponse from(Order order) {
    return new AdminOrderSummaryResponse(
      order.getId(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getContact().getFullName(),
      order.getContact().getEmail(),
      order.getCurrency(),
      order.getTotalInGrosz()
    );
  }
}
