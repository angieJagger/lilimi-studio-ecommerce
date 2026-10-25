package pl.lilimi.auth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.auth.persistence.LoginRateLimitRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class LoginRateLimiter {

  private final LoginRateLimitRepository repository;
  private final Clock clock;
  private final int accountAttempts;
  private final int sourceAttempts;
  private final Duration window;

  public LoginRateLimiter(
    LoginRateLimitRepository repository,
    Clock clock,
    @Value("${lilimi.security.login.account-attempts:5}") int accountAttempts,
    @Value("${lilimi.security.login.source-attempts:30}") int sourceAttempts,
    @Value("${lilimi.security.login.window-seconds:300}") int windowSeconds
  ) {
    if (accountAttempts < 1 || sourceAttempts < 1 || windowSeconds < 1 || windowSeconds > 3600) {
      throw new IllegalArgumentException("Invalid login rate limit configuration");
    }
    this.repository = repository;
    this.clock = clock;
    this.accountAttempts = accountAttempts;
    this.sourceAttempts = sourceAttempts;
    this.window = Duration.ofSeconds(windowSeconds);
  }

  /** Returns zero when admitted, otherwise the minimum retry delay in seconds. */
  @Transactional
  public long reserveAttempt(String email, String source) {
    var now = clock.instant();
    var expiry = now.plus(window);
    long sourceRetry = repository.reserve(hash("source", source), sourceAttempts, now, expiry);
    if (sourceRetry > 0) return sourceRetry;

    String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    return repository.reserve(hash("account", normalizedEmail), accountAttempts, now, expiry);
  }

  @Transactional
  public void removeExpiredBuckets() {
    // Keep a grace period so cleanup never races with an active window.
    repository.deleteExpiredBefore(clock.instant().minus(Duration.ofHours(24)));
  }

  private static String hash(String scope, String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256")
        .digest((scope + ":" + (value == null ? "unknown" : value))
          .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
