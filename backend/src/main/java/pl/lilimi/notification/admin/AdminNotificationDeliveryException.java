package pl.lilimi.notification.admin;

public class AdminNotificationDeliveryException
  extends RuntimeException {

  private final String errorCode;
  private final boolean retryable;

  public AdminNotificationDeliveryException(
    String errorCode,
    boolean retryable
  ) {
    super(errorCode);
    this.errorCode = errorCode;
    this.retryable = retryable;
  }

  public String errorCode() {
    return errorCode;
  }

  public boolean retryable() {
    return retryable;
  }
}
