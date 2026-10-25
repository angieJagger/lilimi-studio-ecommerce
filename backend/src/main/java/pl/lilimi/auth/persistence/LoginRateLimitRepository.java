package pl.lilimi.auth.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class LoginRateLimitRepository {

  private final JdbcTemplate jdbc;

  public LoginRateLimitRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long reserve(String key, int limit, Instant now, Instant expiry) {
    // PostgreSQL locks the conflicting row: concurrent requests cannot overspend
    // the bucket. Rejected requests do not extend the window.
    var accepted = jdbc.query("""
      INSERT INTO login_rate_limits (bucket_key, attempt_count, expires_at)
      VALUES (?, 1, ?)
      ON CONFLICT (bucket_key) DO UPDATE SET
        attempt_count = CASE WHEN login_rate_limits.expires_at <= ?
          THEN 1 ELSE login_rate_limits.attempt_count + 1 END,
        expires_at = CASE WHEN login_rate_limits.expires_at <= ?
          THEN EXCLUDED.expires_at ELSE login_rate_limits.expires_at END
      WHERE login_rate_limits.expires_at <= ?
         OR login_rate_limits.attempt_count < ?
      RETURNING expires_at
      """, (rs, row) -> rs.getTimestamp(1).toInstant(),
      key, Timestamp.from(expiry), Timestamp.from(now), Timestamp.from(now),
      Timestamp.from(now), limit);

    if (!accepted.isEmpty()) return 0;

    Instant retryAt = jdbc.queryForObject(
      "SELECT expires_at FROM login_rate_limits WHERE bucket_key = ?",
      (rs, row) -> rs.getTimestamp(1).toInstant(), key);
    return Math.max(1, (java.time.Duration.between(now, retryAt).toMillis() + 999) / 1000);
  }

  public int deleteExpiredBefore(Instant cutoff) {
    return jdbc.update("DELETE FROM login_rate_limits WHERE expires_at < ?",
      Timestamp.from(cutoff));
  }
}
