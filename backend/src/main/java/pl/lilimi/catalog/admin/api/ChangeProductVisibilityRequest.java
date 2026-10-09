package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeProductVisibilityRequest(
  @NotNull
  Boolean active,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
