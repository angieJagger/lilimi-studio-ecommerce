package pl.lilimi.inquiry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "project_inquiries")
public class ProjectInquiry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Convert(converter = InquiryStatusConverter.class)
  @Column(name = "status", length = 32, nullable = false)
  private InquiryStatus status;

  @Column(name = "language", length = 2, nullable = false)
  private String language;

  @Column(name = "customer_name", length = 150, nullable = false)
  private String name;

  @Column(name = "customer_email", length = 254, nullable = false)
  private String email;

  @Column(name = "project_type", length = 32, nullable = false)
  private String projectType;

  @Column(name = "description", length = 5000, nullable = false)
  private String description;

  @Column(name = "inspiration_url", length = 2048)
  private String inspirationUrl;

  @Column(name = "product_id", length = 100)
  private String productId;

  protected ProjectInquiry() {
    // Required by JPA.
  }

  public ProjectInquiry(
    String language,
    String name,
    String email,
    String projectType,
    String description,
    String inspirationUrl,
    String productId
  ) {
    this.status = InquiryStatus.NEW;
    this.language = Objects.requireNonNull(language);
    this.name = Objects.requireNonNull(name).trim();
    this.email = Objects.requireNonNull(email)
      .trim()
      .toLowerCase(Locale.ROOT);
    this.projectType = Objects.requireNonNull(projectType);
    this.description = Objects.requireNonNull(description).trim();
    this.inspirationUrl = optionalText(inspirationUrl);
    this.productId = optionalText(productId);
  }

  @PrePersist
  private void initializeCreatedAt() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  private static String optionalText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public void changeStatus(InquiryStatus nextStatus) {
    if (nextStatus == null) {
      throw new IllegalArgumentException(
        "Inquiry status is required"
      );
    }

    if (status == nextStatus) {
      return;
    }

    boolean allowed = switch (status) {
      case NEW ->
        nextStatus == InquiryStatus.IN_PROGRESS ||
          nextStatus == InquiryStatus.CLOSED;

      case IN_PROGRESS ->
        nextStatus == InquiryStatus.ANSWERED ||
          nextStatus == InquiryStatus.CLOSED;

      case ANSWERED ->
        nextStatus == InquiryStatus.IN_PROGRESS ||
          nextStatus == InquiryStatus.CLOSED;

      case CLOSED -> false;
    };

    if (!allowed) {
      throw new IllegalStateException(
        "Inquiry status transition is not allowed"
      );
    }

    this.status = nextStatus;
  }

  public UUID getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getStatus() {
    return status.getValue();
  }

  public String getLanguage() {
    return language;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getProjectType() {
    return projectType;
  }

  public String getDescription() {
    return description;
  }

  public String getInspirationUrl() {
    return inspirationUrl;
  }

  public String getProductId() {
    return productId;
  }
}
