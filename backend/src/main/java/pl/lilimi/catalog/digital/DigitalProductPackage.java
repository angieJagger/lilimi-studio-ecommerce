package pl.lilimi.catalog.digital;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "digital_product_packages")
public class DigitalProductPackage {

  @Id
  private UUID id;

  @Column(name = "product_id", length = 64, nullable = false, updatable = false)
  private String productId;

  @Column(name = "package_version", nullable = false, updatable = false)
  private int packageVersion;

  @Column(name = "storage_key", length = 160, nullable = false, updatable = false)
  private String storageKey;

  @Column(name = "original_filename", length = 255, nullable = false, updatable = false)
  private String originalFilename;

  @Column(name = "byte_size", nullable = false, updatable = false)
  private long byteSize;

  @Column(name = "sha256", length = 64, nullable = false, updatable = false)
  private String sha256;

  @Column(name = "current_package", nullable = false)
  private boolean currentPackage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DigitalProductPackage() {
    // Required by JPA.
  }

  public DigitalProductPackage(
    String productId,
    int packageVersion,
    String storageKey,
    String originalFilename,
    long byteSize,
    String sha256,
    Instant createdAt
  ) {
    if (packageVersion < 1) {
      throw new IllegalArgumentException("Package version must be positive");
    }

    if (byteSize < 1) {
      throw new IllegalArgumentException("Package size must be positive");
    }

    if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid SHA-256 checksum");
    }

    this.id = UUID.randomUUID();
    this.productId = requireText(productId, 64, "Product ID");
    this.packageVersion = packageVersion;
    this.storageKey = requireText(storageKey, 160, "Storage key");
    this.originalFilename = requireText(originalFilename, 255, "Filename");
    this.byteSize = byteSize;
    this.sha256 = sha256;
    this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
    this.currentPackage = false;
  }

  public void makeCurrent() {
    this.currentPackage = true;
  }

  public void retire() {
    this.currentPackage = false;
  }

  public UUID getId() {
    return id;
  }

  public String getProductId() {
    return productId;
  }

  public int getPackageVersion() {
    return packageVersion;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public long getByteSize() {
    return byteSize;
  }

  public String getSha256() {
    return sha256;
  }

  public boolean isCurrentPackage() {
    return currentPackage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  private static String requireText(
    String value,
    int maxLength,
    String field
  ) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(field + " is invalid");
    }

    return value;
  }
}
