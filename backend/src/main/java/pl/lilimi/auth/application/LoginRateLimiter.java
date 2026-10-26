package pl.lilimi.auth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.security.application.RequestRateLimiter;

import java.time.Duration;
import java.util.Locale;

@Service
public class LoginRateLimiter {

  private final RequestRateLimiter limiter;
  private final int accountAttempts;
  private final int sourceAttempts;
  private final Duration window;

  public LoginRateLimiter(
    RequestRateLimiter limiter,
    @Value("${lilimi.security.login.account-attempts:5}")
    int accountAttempts,
    @Value("${lilimi.security.login.source-attempts:30}")
    int sourceAttempts,
    @Value("${lilimi.security.login.window-seconds:300}")
    int windowSeconds
  ) {
    if (
      accountAttempts < 1 ||
        sourceAttempts < 1 ||
        windowSeconds < 1 ||
        windowSeconds > 3600
    ) {
      throw new IllegalArgumentException(
        "Invalid login rate limit configuration"
      );
    }

    this.limiter = limiter;
    this.accountAttempts = accountAttempts;
    this.sourceAttempts = sourceAttempts;
    this.window = Duration.ofSeconds(windowSeconds);
  }

  @Transactional
  public long reserveAttempt(String email, String source) {
    long sourceRetry = limiter.reserve(
      "source",
      source,
      sourceAttempts,
      window
    );

    if (sourceRetry > 0) {
      return sourceRetry;
    }

    String normalizedEmail = email == null
      ? ""
      : email.trim().toLowerCase(Locale.ROOT);

    return limiter.reserve(
      "account",
      normalizedEmail,
      accountAttempts,
      window
    );
  }
}
