package pl.lilimi.auth.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record RegisterCustomerRequest(
  @NotBlank
  @Email
  @Size(max = 254)
  String email,

  @NotBlank
  @Size(min = 12, max = 72)
  String password
) {

  @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
  public boolean isPasswordWithinByteLimit() {
    return password == null
      || password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  @Override
  public String toString() {
    return "RegisterCustomerRequest[credentials=REDACTED]";
  }
}
