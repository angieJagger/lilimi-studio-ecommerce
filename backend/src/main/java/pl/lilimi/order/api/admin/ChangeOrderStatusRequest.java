package pl.lilimi.order.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeOrderStatusRequest(
  @NotBlank
  @Pattern(regexp = "new|processing|completed|cancelled")
  String status,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
