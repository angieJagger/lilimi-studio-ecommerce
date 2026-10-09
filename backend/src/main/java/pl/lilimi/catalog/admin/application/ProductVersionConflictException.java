package pl.lilimi.catalog.admin.application;

public class ProductVersionConflictException
  extends RuntimeException {

  public ProductVersionConflictException() {
    super(
      "Product was modified. Reload the product list before updating."
    );
  }
}
