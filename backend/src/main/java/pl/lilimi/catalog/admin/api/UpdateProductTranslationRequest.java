package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProductTranslationRequest(
  @NotBlank
  @Size(max = 200)
  String name,

  @NotBlank
  @Size(max = 5000)
  String description
) {
}
