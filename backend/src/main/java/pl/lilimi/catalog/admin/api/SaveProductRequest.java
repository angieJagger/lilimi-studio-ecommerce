package pl.lilimi.catalog.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SaveProductRequest(
  @NotNull @PositiveOrZero Long expectedVersion,
  @NotNull @Valid UpdateProductTranslationRequest pl,
  @NotNull @Valid UpdateProductTranslationRequest en,
  @Positive Integer digitalPriceInGrosz,
  @NotNull @Size(max = 10) List<@NotNull @Valid SaveProductImageRequest> images
) {}
