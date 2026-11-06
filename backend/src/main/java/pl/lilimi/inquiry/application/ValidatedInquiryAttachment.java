package pl.lilimi.inquiry.application;

public record ValidatedInquiryAttachment(
  String originalFilename,
  String contentType,
  byte[] content
) {
}
