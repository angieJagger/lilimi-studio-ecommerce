package pl.lilimi.security.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.security.persistence.RateLimitRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class RequestRateLimiter {

  private final RateLimitRepository repository;
  private final Clock clock;

  public RequestRateLimiter(
    RateLimitRepository repository,
    Clock clock
  ) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * Returns zero when admitted, otherwise the retry delay in seconds.
   */
  @Transactional
  public long reserve(
    String scope,
    String identifier,
    int limit,
    Duration window
  ) {
    validatePolicy(scope, limit, window);

    var now = clock.instant();

    return repository.reserve(
      hash(scope, identifier),
      limit,
      now,
      now.plus(window)
    );
  }

  @Transactional
  public void removeExpiredBuckets() {
    repository.deleteExpiredBefore(
      clock.instant().minus(Duration.ofHours(24))
    );
  }

  private void validatePolicy(
    String scope,
    int limit,
    Duration window
  ) {
    if (
      scope == null ||
        scope.isBlank() ||
        limit < 1 ||
        window == null ||
        window.compareTo(Duration.ofSeconds(1)) < 0 ||
        window.compareTo(Duration.ofDays(1)) > 0
    ) {
      throw new IllegalArgumentException(
        "Invalid rate limit policy"
      );
    }
  }

  private String hash(String scope, String identifier) {
    String value = identifier == null ? "unknown" : identifier;

    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(
          (scope + ":" + value)
            .getBytes(StandardCharsets.UTF_8)
        );

      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
        "SHA-256 is unavailable",
        exception
      );
    }
  }
}
