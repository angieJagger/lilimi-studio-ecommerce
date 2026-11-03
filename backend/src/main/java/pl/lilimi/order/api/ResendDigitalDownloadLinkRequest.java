package pl.lilimi.order.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ResendDigitalDownloadLinkRequest(
  @NotNull
  UUID orderId,

  @NotBlank
  @Email
  @Size(max = 254)
  String email
) {

  @Override
  public String toString() {
    return "ResendDigitalDownloadLinkRequest[details=[REDACTED]]";
  }
}
