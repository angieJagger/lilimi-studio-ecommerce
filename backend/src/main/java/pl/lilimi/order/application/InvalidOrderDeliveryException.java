package pl.lilimi.order.application;

public class InvalidOrderDeliveryException extends RuntimeException {

  public InvalidOrderDeliveryException(String message) {
    super(message);
  }
}
