package pl.lilimi.order.application;

public class OrderRateLimitExceededException
  extends RuntimeException {

  private final long retryAfterSeconds;

  public OrderRateLimitExceededException(
    long retryAfterSeconds
  ) {
    super("Order creation rate limit exceeded");

    if (retryAfterSeconds < 1) {
      throw new IllegalArgumentException(
        "Retry delay must be positive"
      );
    }

    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
