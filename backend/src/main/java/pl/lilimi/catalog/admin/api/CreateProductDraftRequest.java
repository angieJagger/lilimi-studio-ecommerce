package pl.lilimi.catalog.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.lilimi.catalog.PhysicalDraftConfiguration;

public record CreateProductDraftRequest(
  @NotNull @Valid UpdateProductTranslationRequest pl,
  @NotNull @Valid UpdateProductTranslationRequest en,

  @Pattern(regexp = "digital|physical")
  String productType,

  @Size(max = 64)
  @Pattern(regexp = "(?!(?:pattern|physical)-[0-9]+$)[a-z0-9]+(?:-[a-z0-9]+)*")
  String productCode,
  @Valid PhysicalDraftConfiguration physicalConfiguration,
  @Positive Integer digitalPriceInGrosz,
  @PositiveOrZero Integer stockQuantity
) {
  public CreateProductDraftRequest {
    if (productType == null) {
      productType = "digital";
    }
  }
}
