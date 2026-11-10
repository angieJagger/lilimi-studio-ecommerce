package pl.lilimi.catalog.admin.application;

public class ProductSlugConflictException extends RuntimeException {

  public ProductSlugConflictException(Throwable cause) {
    super("A product with this slug already exists.", cause);
  }
}
