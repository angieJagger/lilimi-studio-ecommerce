package pl.lilimi.order.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderContactRequest(
  @NotBlank
  @Size(max = 150)
  String fullName,

  @NotBlank
  @Email
  @Size(max = 254)
  String email,

  @Size(max = 30)
  String phone
) {
}
