package pl.lilimi.inquiry.application.admin;

import java.util.Objects;

public class InquiryStatusConflictException
  extends RuntimeException {

  private final String code;

  public InquiryStatusConflictException(
    String code,
    String message
  ) {
    super(message);
    this.code = Objects.requireNonNull(code);
  }

  public String getCode() {
    return code;
  }
}
