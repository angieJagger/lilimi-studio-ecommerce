package pl.lilimi.inquiry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inquiry_attachments")
public class InquiryAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "inquiry_id", nullable = false, updatable = false)
  private UUID inquiryId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(
    name = "original_filename",
    length = 255,
    nullable = false,
    updatable = false
  )
  private String originalFilename;

  @Column(
    name = "storage_key",
    length = 255,
    nullable = false,
    updatable = false
  )
  private String storageKey;

  @Column(
    name = "content_type",
    length = 100,
    nullable = false,
    updatable = false
  )
  private String contentType;

  @Column(name = "byte_size", nullable = false, updatable = false)
  private long byteSize;

  protected InquiryAttachment() {
    // Required by JPA.
  }

  public InquiryAttachment(
    UUID inquiryId,
    String originalFilename,
    String storageKey,
    String contentType,
    long byteSize
  ) {
    this.inquiryId = Objects.requireNonNull(inquiryId);
    this.originalFilename = Objects.requireNonNull(originalFilename);
    this.storageKey = Objects.requireNonNull(storageKey);
    this.contentType = Objects.requireNonNull(contentType);

    if (byteSize <= 0 || byteSize > 10L * 1024 * 1024) {
      throw new IllegalArgumentException(
        "Attachment size must be between 1 byte and 10 MiB"
      );
    }

    this.byteSize = byteSize;
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

  public UUID getInquiryId() {
    return inquiryId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getByteSize() {
    return byteSize;
  }
}
