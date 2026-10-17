package pl.lilimi.payment.domain;

public enum PaymentAttemptStatus {

  CREATED("created"),
  PENDING("pending"),
  SUCCEEDED("succeeded"),
  FAILED("failed"),
  EXPIRED("expired");

  private final String value;

  PaymentAttemptStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static PaymentAttemptStatus fromValue(String value) {
    for (var status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }

    throw new IllegalArgumentException(
      "Unknown payment attempt status"
    );
  }
}
