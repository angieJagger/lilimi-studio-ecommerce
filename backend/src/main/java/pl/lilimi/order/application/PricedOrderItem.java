package pl.lilimi.order.application;

import pl.lilimi.order.api.OrderItemRequest;

import java.util.Objects;

public record PricedOrderItem(
  OrderItemRequest item,
  String productName,
  String patternName,
  long unitPriceInGrosz
) {

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

    Integer quantity = switch (item) {
      case OrderItemRequest.Digital digital -> digital.quantity();
      case OrderItemRequest.Sweatshirt sweatshirt -> sweatshirt.quantity();
    };

    if (quantity == null || quantity < 1 || quantity > 99) {
      throw new IllegalArgumentException("Invalid order item quantity");
    }

    if (item instanceof OrderItemRequest.Digital && quantity != 1) {
      throw new IllegalArgumentException("Digital quantity must be one");
    }
  }

  public int quantity() {
    return switch (item) {
      case OrderItemRequest.Digital digital -> digital.quantity();
      case OrderItemRequest.Sweatshirt sweatshirt -> sweatshirt.quantity();
    };
  }

  public long lineTotalInGrosz() {
    return Math.multiplyExact(unitPriceInGrosz, quantity());
  }
}
