package pl.lilimi.inquiry.api.admin;

import pl.lilimi.inquiry.domain.ProjectInquiry;

import java.time.Instant;
import java.util.UUID;

public record AdminInquirySummaryResponse(
  UUID id,
  Instant createdAt,
  String status,
  String name,
  String email,
  String projectType,
  String productId
) {

  public static AdminInquirySummaryResponse from(
    ProjectInquiry inquiry
  ) {
    return new AdminInquirySummaryResponse(
      inquiry.getId(),
      inquiry.getCreatedAt(),
      inquiry.getStatus(),
      inquiry.getName(),
      inquiry.getEmail(),
      inquiry.getProjectType(),
      inquiry.getProductId()
    );
  }
}
