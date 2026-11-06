package pl.lilimi.inquiry.application;

public class InvalidInquiryAttachmentException extends RuntimeException {

  public InvalidInquiryAttachmentException(String message) {
    super(message);
  }

  public InvalidInquiryAttachmentException(
    String message,
    Throwable cause
  ) {
    super(message, cause);
  }
}
