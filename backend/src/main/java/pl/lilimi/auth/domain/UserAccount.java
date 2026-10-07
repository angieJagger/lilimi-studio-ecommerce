package pl.lilimi.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email", length = 254, nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", length = 255, nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 16, nullable = false)
  private UserRole role;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected UserAccount() {
    // Required by JPA.
  }

  public UserAccount(
    String email,
    String passwordHash,
    UserRole role
  ) {
    Objects.requireNonNull(email, "Email is required");
    Objects.requireNonNull(passwordHash, "Password hash is required");
    Objects.requireNonNull(role, "Role is required");

    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    if (normalizedEmail.isBlank() || normalizedEmail.length() > 254) {
      throw new IllegalArgumentException("Invalid email");
    }

    if (passwordHash.isBlank() || passwordHash.length() > 255) {
      throw new IllegalArgumentException("Invalid password hash");
    }

    this.email = normalizedEmail;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = true;
  }

  @PrePersist
  private void initializeCreatedAt() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void disable() {
    this.enabled = false;
  }
}
