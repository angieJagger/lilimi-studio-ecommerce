package pl.lilimi.catalog.admin.application;

public class GarmentVariantVersionConflictException
  extends RuntimeException {

  public GarmentVariantVersionConflictException() {
    super("Variant was modified. Reload variants before updating.");
  }
}
