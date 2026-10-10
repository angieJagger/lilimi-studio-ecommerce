package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateDigitalProductPriceRequest(
  @NotNull
  @Positive
  Integer priceInGrosz,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
