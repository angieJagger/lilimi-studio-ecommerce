package pl.lilimi.inquiry.api.admin;

import pl.lilimi.inquiry.domain.ProjectInquiry;

import java.time.Instant;
import java.util.UUID;

public record AdminInquiryDetailsResponse(
  UUID id,
  long version,
  Instant createdAt,
  String status,
  String language,
  String name,
  String email,
  String projectType,
  String description,
  String inspirationUrl,
  String productId
) {

  public static AdminInquiryDetailsResponse from(
    ProjectInquiry inquiry
  ) {
    return new AdminInquiryDetailsResponse(
      inquiry.getId(),
      inquiry.getVersion(),
      inquiry.getCreatedAt(),
      inquiry.getStatus(),
      inquiry.getLanguage(),
      inquiry.getName(),
      inquiry.getEmail(),
      inquiry.getProjectType(),
      inquiry.getDescription(),
      inquiry.getInspirationUrl(),
      inquiry.getProductId()
    );
  }
}
