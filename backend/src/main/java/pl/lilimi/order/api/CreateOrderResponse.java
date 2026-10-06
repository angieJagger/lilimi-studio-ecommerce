package pl.lilimi.order.api;

import pl.lilimi.order.domain.Order;

import java.time.Instant;
import java.util.UUID;

public record CreateOrderResponse(
  UUID id,
  Instant createdAt,
  String status,
  String currency,
  long subtotalInGrosz,
  long deliveryPriceInGrosz,
  long totalInGrosz
) {

  public static CreateOrderResponse from(Order order) {
    return new CreateOrderResponse(
      order.getId(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getCurrency(),
      order.getSubtotalInGrosz(),
      order.getDeliveryPriceInGrosz(),
      order.getTotalInGrosz()
    );
  }
}
