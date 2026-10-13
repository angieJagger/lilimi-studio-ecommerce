package pl.lilimi.order.api.customer;

import pl.lilimi.order.domain.Order;

import java.time.Instant;
import java.util.UUID;

public record CustomerOrderSummaryResponse(
  UUID id,
  Instant createdAt,
  String status,
  String currency,
  long totalInGrosz
) {

  public static CustomerOrderSummaryResponse from(Order order) {
    return new CustomerOrderSummaryResponse(
      order.getId(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getCurrency(),
      order.getTotalInGrosz()
    );
  }
}
