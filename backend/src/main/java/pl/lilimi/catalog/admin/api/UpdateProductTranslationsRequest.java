package pl.lilimi.catalog.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateProductTranslationsRequest(
  @NotNull
  @Valid
  UpdateProductTranslationRequest pl,

  @NotNull
  @Valid
  UpdateProductTranslationRequest en,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
