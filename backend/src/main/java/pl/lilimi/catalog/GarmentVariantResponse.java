package pl.lilimi.catalog;

import java.util.UUID;

public record GarmentVariantResponse(
  UUID id,
  String patternId,
  String fit,
  String size,
  String color,
  String embroideryOptionId,
  int widthMm,
  int heightMm,
  String placement,
  int priceInGrosz,
  String currency
) {
}
