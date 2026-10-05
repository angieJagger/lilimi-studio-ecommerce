package pl.lilimi.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SweatshirtConfigurationRequest(
  @NotBlank
  @Size(max = 50)
  String fit,

  @NotBlank
  @Size(max = 20)
  String size,

  @NotBlank
  @Size(max = 50)
  String color,

  @NotBlank
  @Size(max = 100)
  String embroideryOptionId
) {
}
