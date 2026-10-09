package pl.lilimi.inquiry.api;

import java.time.Instant;
import java.util.UUID;

import pl.lilimi.inquiry.domain.ProjectInquiry;

public record CreateInquiryResponse(
  UUID id,
  Instant createdAt,
  String status
) {

  public static CreateInquiryResponse from(ProjectInquiry inquiry) {
    return new CreateInquiryResponse(
      inquiry.getId(),
      inquiry.getCreatedAt(),
      inquiry.getStatus()
    );
  }
}
