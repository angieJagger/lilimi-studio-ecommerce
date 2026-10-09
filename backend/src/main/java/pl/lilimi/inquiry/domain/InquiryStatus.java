package pl.lilimi.inquiry.domain;

public enum InquiryStatus {
  NEW("new"),
  IN_PROGRESS("in_progress"),
  ANSWERED("answered"),
  CLOSED("closed");

  private final String value;

  InquiryStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static InquiryStatus fromValue(String value) {
    for (var status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }

    throw new IllegalArgumentException(
      "Unknown inquiry status: " + value
    );
  }
}
