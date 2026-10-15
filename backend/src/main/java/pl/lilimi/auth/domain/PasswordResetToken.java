package pl.lilimi.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(
    name = "token_hash",
    length = 64,
    nullable = false,
    updatable = false
  )
  private String tokenHash;

  @Column(
    name = "credentials_version",
    nullable = false,
    updatable = false
  )
  private long credentialsVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  protected PasswordResetToken() {
    // Required by JPA.
  }

  public PasswordResetToken(
    UUID accountId,
    String tokenHash,
    long credentialsVersion,
    Instant createdAt,
    Instant expiresAt
  ) {
    this.accountId = Objects.requireNonNull(accountId);
    this.tokenHash = Objects.requireNonNull(tokenHash);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);

    if (!tokenHash.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid token hash");
    }

    if (credentialsVersion < 0) {
      throw new IllegalArgumentException("Invalid credentials version");
    }

    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("Invalid token expiry");
    }

    this.id = UUID.randomUUID();
    this.credentialsVersion = credentialsVersion;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public long getCredentialsVersion() {
    return credentialsVersion;
  }

  public boolean isUsableAt(Instant now) {
    Objects.requireNonNull(now);

    return consumedAt == null
      && !now.isBefore(createdAt)
      && now.isBefore(expiresAt);
  }

  public void consume(Instant now) {
    if (!isUsableAt(now)) {
      throw new IllegalStateException("Token is expired or already used");
    }

    this.consumedAt = now;
  }
}
