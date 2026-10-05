package pl.lilimi.order.application;

public class OrderProductUnavailableException extends RuntimeException {

  private final String productId;

  public OrderProductUnavailableException(String productId) {
    super("Product or selected variant is unavailable: " + productId);
    this.productId = productId;
  }

  public String getProductId() {
    return productId;
  }
}
