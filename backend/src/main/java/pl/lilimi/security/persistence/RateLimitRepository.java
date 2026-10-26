package pl.lilimi.security.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Repository
public class RateLimitRepository {

  private final JdbcTemplate jdbc;

  public RateLimitRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long reserve(
    String key,
    int limit,
    Instant now,
    Instant expiry
  ) {
    // Concurrent requests cannot consume more than the available budget.
    // Rejected requests do not extend the window.
    var accepted = jdbc.query("""
      INSERT INTO request_rate_limits (
        bucket_key, attempt_count, expires_at
      )
      VALUES (?, 1, ?)
      ON CONFLICT (bucket_key) DO UPDATE SET
        attempt_count = CASE
          WHEN request_rate_limits.expires_at <= ?
          THEN 1
          ELSE request_rate_limits.attempt_count + 1
        END,
        expires_at = CASE
          WHEN request_rate_limits.expires_at <= ?
          THEN EXCLUDED.expires_at
          ELSE request_rate_limits.expires_at
        END
      WHERE request_rate_limits.expires_at <= ?
         OR request_rate_limits.attempt_count < ?
      RETURNING expires_at
      """,
      (rs, row) -> rs.getTimestamp(1).toInstant(),
      key,
      Timestamp.from(expiry),
      Timestamp.from(now),
      Timestamp.from(now),
      Timestamp.from(now),
      limit
    );

    if (!accepted.isEmpty()) {
      return 0;
    }

    Instant retryAt = jdbc.queryForObject(
      """
      SELECT expires_at
      FROM request_rate_limits
      WHERE bucket_key = ?
      """,
      (rs, row) -> rs.getTimestamp(1).toInstant(),
      key
    );

    long remainingMillis = Duration.between(now, retryAt).toMillis();
    return Math.max(1, (remainingMillis + 999) / 1000);
  }

  public int deleteExpiredBefore(Instant cutoff) {
    return jdbc.update(
      "DELETE FROM request_rate_limits WHERE expires_at < ?",
      Timestamp.from(cutoff)
    );
  }
}
