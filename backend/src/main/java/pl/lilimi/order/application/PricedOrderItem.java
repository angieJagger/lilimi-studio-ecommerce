package pl.lilimi.order.application;

import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.catalog.GarmentVariant;

import java.util.Objects;

public record PricedOrderItem(
  OrderItemRequest item,
  String productName,
  String patternName,
  long unitPriceInGrosz,
  GarmentVariant garmentVariant
) {

  public PricedOrderItem(OrderItemRequest item, String productName, String patternName, long unitPriceInGrosz) {
    this(item, productName, patternName, unitPriceInGrosz, null);
  }

  public PricedOrderItem {
    Objects.requireNonNull(item, "Order item is required");
    Objects.requireNonNull(productName, "Product name is required");

    if (productName.isBlank() || unitPriceInGrosz <= 0) {
      throw new IllegalArgumentException("Invalid priced order item");
    }

    if (item instanceof OrderItemRequest.Sweatshirt
      && (patternName == null || patternName.isBlank())) {
      throw new IllegalArgumentException("Pattern name is required");
    }

    Integer quantity = item.quantity();

    if (quantity == null || quantity < 1) {
      throw new IllegalArgumentException("Invalid order item quantity");
    }

    if (item instanceof OrderItemRequest.Digital && quantity != 1) {
      throw new IllegalArgumentException("Digital quantity must be one");
    }
  }

  public int quantity() {
    return item.quantity();
  }

  public long lineTotalInGrosz() {
    return Math.multiplyExact(unitPriceInGrosz, quantity());
  }
}
