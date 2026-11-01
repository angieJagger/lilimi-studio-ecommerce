package pl.lilimi.order.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IssuedDigitalDownloadToken(
  UUID orderId,
  String rawToken,
  Instant expiresAt
) {

  public IssuedDigitalDownloadToken {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(rawToken);
    Objects.requireNonNull(expiresAt);
  }

  @Override
  public String toString() {
    return "IssuedDigitalDownloadToken[orderId="
      + orderId
      + ", rawToken=[REDACTED], expiresAt="
      + expiresAt
      + "]";
  }
}
