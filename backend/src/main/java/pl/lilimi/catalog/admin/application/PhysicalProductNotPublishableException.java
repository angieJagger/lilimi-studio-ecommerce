package pl.lilimi.catalog.admin.application;

public class PhysicalProductNotPublishableException extends RuntimeException {

  public PhysicalProductNotPublishableException() {
    super("A price and available stock are required to publish this physical product");
  }
}
