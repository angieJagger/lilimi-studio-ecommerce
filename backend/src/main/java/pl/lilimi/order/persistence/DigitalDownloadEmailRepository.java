package pl.lilimi.order.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class DigitalDownloadEmailRepository {

  private final JdbcTemplate jdbc;

  public DigitalDownloadEmailRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void enqueue(UUID orderId) {
    Objects.requireNonNull(orderId);

    jdbc.update("""
        INSERT INTO digital_download_emails (order_id)
        VALUES (?)
        ON CONFLICT (order_id) DO NOTHING
        """,
      orderId
    );
  }

  public boolean enqueueAgain(
    UUID orderId,
    Instant now,
    Duration cooldown
  ) {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(now);
    Objects.requireNonNull(cooldown);

    if (cooldown.isZero() || cooldown.isNegative()) {
      throw new IllegalArgumentException(
        "Download email cooldown must be positive"
      );
    }

    int updated = jdbc.update("""
      INSERT INTO digital_download_emails (
          order_id,
          created_at,
          next_attempt_at,
          last_requested_at
      )
      VALUES (?, ?, ?, ?)
      ON CONFLICT (order_id) DO UPDATE
      SET status = 'pending',
          attempts = 0,
          next_attempt_at = EXCLUDED.next_attempt_at,
          last_requested_at = EXCLUDED.last_requested_at,
          sent_at = NULL,
          last_error_code = NULL
      WHERE digital_download_emails.status <> 'pending'
        AND GREATEST(
          digital_download_emails.last_requested_at,
          COALESCE(
            digital_download_emails.sent_at,
            digital_download_emails.last_requested_at
          )
        ) <= ?
      """,
      orderId,
      Timestamp.from(now),
      Timestamp.from(now),
      Timestamp.from(now),
      Timestamp.from(now.minus(cooldown))
    );

    return updated == 1;
  }

  public Optional<PendingEmail> lockNext(Instant now) {
    Objects.requireNonNull(now);

    return jdbc.query("""
        SELECT order_id, attempts
        FROM digital_download_emails
        WHERE status = 'pending'
          AND next_attempt_at <= ?
        ORDER BY next_attempt_at, created_at, order_id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """,
      (resultSet, rowNumber) -> new PendingEmail(
        resultSet.getObject("order_id", UUID.class),
        resultSet.getInt("attempts")
      ),
      Timestamp.from(now)
    ).stream().findFirst();
  }

  public void markSent(UUID orderId, Instant sentAt) {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(sentAt);

    int updated = jdbc.update("""
        UPDATE digital_download_emails
        SET status = 'sent',
            attempts = attempts + 1,
            sent_at = ?,
            last_error_code = NULL
        WHERE order_id = ?
          AND status = 'pending'
        """,
      Timestamp.from(sentAt),
      orderId
    );

    requireUpdated(updated);
  }

  public void markSkipped(UUID orderId, String reason) {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(reason);

    int updated = jdbc.update("""
        UPDATE digital_download_emails
        SET status = 'skipped',
            sent_at = NULL,
            last_error_code = ?
        WHERE order_id = ?
          AND status = 'pending'
        """,
      reason,
      orderId
    );

    requireUpdated(updated);
  }

  public void recordFailure(
    UUID orderId,
    boolean retry,
    Instant nextAttemptAt,
    String errorCode
  ) {
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(nextAttemptAt);
    Objects.requireNonNull(errorCode);

    int updated = jdbc.update("""
        UPDATE digital_download_emails
        SET status = ?,
            attempts = attempts + 1,
            next_attempt_at = ?,
            sent_at = NULL,
            last_error_code = ?
        WHERE order_id = ?
          AND status = 'pending'
        """,
      retry ? "pending" : "failed",
      Timestamp.from(nextAttemptAt),
      errorCode,
      orderId
    );

    requireUpdated(updated);
  }

  private static void requireUpdated(int updated) {
    if (updated != 1) {
      throw new IllegalStateException(
        "Could not update digital download email queue entry"
      );
    }
  }

  public record PendingEmail(
    UUID orderId,
    int attempts
  ) {
  }
}
