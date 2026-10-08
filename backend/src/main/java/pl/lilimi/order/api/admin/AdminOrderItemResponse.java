package pl.lilimi.order.api.admin;

import pl.lilimi.order.domain.OrderItem;

public record AdminOrderItemResponse(
  String kind,
  String productId,
  String productName,
  String patternId,
  String patternName,
  String fit,
  String size,
  String color,
  String embroideryOptionId,
  int quantity,
  long unitPriceInGrosz,
  long lineTotalInGrosz
) {

  public static AdminOrderItemResponse from(OrderItem item) {
    return new AdminOrderItemResponse(
      item.getKind(),
      item.getProductId(),
      item.getProductName(),
      item.getPatternId(),
      item.getPatternName(),
      item.getFit(),
      item.getSize(),
      item.getColor(),
      item.getEmbroideryOptionId(),
      item.getQuantity(),
      item.getUnitPriceInGrosz(),
      item.getLineTotalInGrosz()
    );
  }
}
