package pl.lilimi.auth.mfa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.security.application.RequestRateLimiter;

import java.time.Duration;
import java.util.UUID;

@Service
public class AdminMfaRateLimiter {

  private static final int ATTEMPTS = 5;
  private static final Duration WINDOW = Duration.ofMinutes(5);

  private final RequestRateLimiter limiter;

  public AdminMfaRateLimiter(RequestRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public long reserveAttempt(UUID accountId) {
    return limiter.reserve(
      "admin-mfa-account",
      accountId.toString(),
      ATTEMPTS,
      WINDOW
    );
  }
}
