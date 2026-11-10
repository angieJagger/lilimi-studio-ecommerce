package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateGarmentVariantRequest(
  @NotNull
  @Positive
  Integer priceInGrosz,

  @NotNull
  Boolean active,

  @PositiveOrZero
  Integer stockQuantity,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
