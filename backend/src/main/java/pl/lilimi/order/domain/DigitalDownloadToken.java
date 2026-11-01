package pl.lilimi.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "digital_download_tokens")
public class DigitalDownloadToken {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(
    name = "token_hash",
    length = 64,
    nullable = false,
    updatable = false
  )
  private String tokenHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected DigitalDownloadToken() {
    // Required by JPA.
  }

  public DigitalDownloadToken(
    UUID orderId,
    String tokenHash,
    Instant createdAt,
    Instant expiresAt
  ) {
    this.orderId = Objects.requireNonNull(orderId);
    this.tokenHash = Objects.requireNonNull(tokenHash);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);

    if (!tokenHash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid token hash");
    }

    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("Invalid token expiry");
    }

    this.id = UUID.randomUUID();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isUsableAt(Instant now) {
    Objects.requireNonNull(now);

    return revokedAt == null
      && !now.isBefore(createdAt)
      && now.isBefore(expiresAt);
  }

  public void revoke(Instant now) {
    Objects.requireNonNull(now);

    if (now.isBefore(createdAt)) {
      throw new IllegalArgumentException(
        "Revocation cannot precede token creation"
      );
    }

    if (revokedAt == null) {
      revokedAt = now;
    }
  }
}
