package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SaveProductImageRequest(
  UUID id,
  @PositiveOrZero Integer fileIndex,
  @NotNull @Size(max = 250) String altPl,
  @NotNull @Size(max = 250) String altEn,
  boolean primaryImage
) {}
