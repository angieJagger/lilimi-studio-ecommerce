package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReplaceProductImageRequest(
  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
