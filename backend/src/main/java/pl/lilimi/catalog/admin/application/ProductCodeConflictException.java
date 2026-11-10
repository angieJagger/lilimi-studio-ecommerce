package pl.lilimi.catalog.admin.application;

public class ProductCodeConflictException extends RuntimeException {

  public ProductCodeConflictException(Throwable cause) {
    super("A product with this code already exists.", cause);
  }
}
