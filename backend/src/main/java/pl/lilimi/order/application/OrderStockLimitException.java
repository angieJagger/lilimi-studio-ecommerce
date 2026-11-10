package pl.lilimi.order.application;

import java.util.UUID;

public class OrderStockLimitException extends RuntimeException {
  private final String productId;
  private final int maxQuantity;
  private final UUID variantId;

  public OrderStockLimitException(String productId, int maxQuantity) {
    this(productId, maxQuantity, null);
  }

  public OrderStockLimitException(String productId, int maxQuantity, UUID variantId) {
    super("Requested quantity exceeds available stock");
    this.productId = productId;
    this.maxQuantity = maxQuantity;
    this.variantId = variantId;
  }

  public UUID getVariantId() {
    return variantId;
  }

  public String getProductId() {
    return productId;
  }

  public int getMaxQuantity() {
    return maxQuantity;
  }
}
