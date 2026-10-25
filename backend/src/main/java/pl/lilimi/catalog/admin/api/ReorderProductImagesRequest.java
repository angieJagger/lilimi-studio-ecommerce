package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ReorderProductImagesRequest(
  @NotNull
  @Size(min = 1, max = 10)
  List<@NotNull UUID> imageIds,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
