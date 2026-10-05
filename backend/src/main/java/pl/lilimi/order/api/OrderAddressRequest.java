package pl.lilimi.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderAddressRequest(
  @NotBlank
  @Size(max = 200)
  String addressLine1,

  @Size(max = 200)
  String addressLine2,

  @NotBlank
  @Pattern(regexp = "\\d{2}-\\d{3}")
  String postalCode,

  @NotBlank
  @Size(max = 100)
  String city,

  @NotBlank
  @Pattern(regexp = "PL")
  String countryCode
) {
}
