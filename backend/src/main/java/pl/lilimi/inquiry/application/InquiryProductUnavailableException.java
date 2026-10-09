package pl.lilimi.inquiry.application;

public class InquiryProductUnavailableException extends RuntimeException {

  private final String productId;

  public InquiryProductUnavailableException(String productId) {
    super("The referenced product is unavailable");
    this.productId = productId;
  }

  public String getProductId() {
    return productId;
  }
}
