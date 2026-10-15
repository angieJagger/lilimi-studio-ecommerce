package pl.lilimi.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
  @NotBlank
  @Email
  @Size(max = 254)
  String email,

  @Pattern(regexp = "pl|en")
  String language
) {
}
