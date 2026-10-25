package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UploadProductImageRequest(
  @NotNull
  @Size(max = 250)
  String altPl,

  @NotNull
  @Size(max = 250)
  String altEn,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
