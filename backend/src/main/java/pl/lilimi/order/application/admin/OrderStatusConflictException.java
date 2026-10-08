package pl.lilimi.order.application.admin;

public class OrderStatusConflictException extends RuntimeException {

  private final String code;

  public OrderStatusConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
