package pl.lilimi.security.application;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class PublicFormRateLimiter {

  private final RequestRateLimiter limiter;
  private static final int INQUIRY_ATTEMPTS = 5;
  private static final Duration INQUIRY_WINDOW =
    Duration.ofMinutes(15);
  private static final int REGISTRATION_ATTEMPTS = 10;
  private static final Duration REGISTRATION_WINDOW =
    Duration.ofHours(1);
  private static final int ORDER_ATTEMPTS = 10;
  private static final Duration ORDER_WINDOW =
    Duration.ofMinutes(15);

  public PublicFormRateLimiter(RequestRateLimiter limiter) {
    this.limiter = limiter;
  }

  public long reserveInquiry(String source) {
    return limiter.reserve(
      "inquiry-source",
      source,
      INQUIRY_ATTEMPTS,
      INQUIRY_WINDOW
    );
  }

  public long reserveRegistration(String source) {
    return limiter.reserve(
      "registration-source",
      source,
      REGISTRATION_ATTEMPTS,
      REGISTRATION_WINDOW
    );
  }

  public long reserveOrder(String source) {
    return limiter.reserve(
      "order-source",
      source,
      ORDER_ATTEMPTS,
      ORDER_WINDOW
    );
  }
}
