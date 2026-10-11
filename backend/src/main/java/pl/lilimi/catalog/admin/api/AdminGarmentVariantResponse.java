package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.GarmentVariant;

import java.util.UUID;

public record AdminGarmentVariantResponse(
  UUID id,
  long version,
  String productId,
  String patternId,
  String embroideryOptionId,
  String fit,
  String size,
  String color,
  int priceInGrosz,
  String currency,
  boolean active
) {

  public static AdminGarmentVariantResponse from(
    GarmentVariant variant
  ) {
    return new AdminGarmentVariantResponse(
      variant.getId(),
      variant.getVersion(),
      variant.getProductId(),
      variant.getPatternId(),
      variant.getEmbroideryOptionId(),
      variant.getFit(),
      variant.getSize(),
      variant.getColor(),
      variant.getPriceInGrosz(),
      "PLN",
      variant.isActive()
    );
  }
}
