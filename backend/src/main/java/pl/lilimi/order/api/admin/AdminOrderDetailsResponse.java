package pl.lilimi.order.api.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminOrderDetailsResponse(
  UUID id,
  long version,
  Instant createdAt,
  String status,
  String language,
  String currency,
  AdminOrderContactResponse contact,
  AdminOrderDeliveryResponse delivery,
  List<AdminOrderItemResponse> items,
  long subtotalInGrosz,
  long deliveryPriceInGrosz,
  long totalInGrosz
) {
}
