package pl.lilimi.inquiry.api.admin;

import pl.lilimi.inquiry.domain.InquiryAttachment;

import java.time.Instant;
import java.util.UUID;

public record AdminInquiryAttachmentResponse(
  UUID id,
  String originalFilename,
  String contentType,
  long byteSize,
  Instant createdAt
) {

  public static AdminInquiryAttachmentResponse from(
    InquiryAttachment attachment
  ) {
    return new AdminInquiryAttachmentResponse(
      attachment.getId(),
      attachment.getOriginalFilename(),
      attachment.getContentType(),
      attachment.getByteSize(),
      attachment.getCreatedAt()
    );
  }
}
