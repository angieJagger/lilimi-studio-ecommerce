package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangePrimaryProductImageRequest(
  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
