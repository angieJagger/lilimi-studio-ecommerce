package pl.lilimi.order.application;

public class OrderSubmissionConflictException extends RuntimeException {

  public OrderSubmissionConflictException() {
    super("Idempotency key was already used for different order data");
  }
}
