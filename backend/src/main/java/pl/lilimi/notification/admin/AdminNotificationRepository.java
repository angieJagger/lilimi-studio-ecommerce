package pl.lilimi.notification.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AdminNotificationRepository {

  private static final int MAX_ATTEMPTS = 8;
  private static final long LEASE_SECONDS = 120;

  private final JdbcTemplate jdbc;

  public AdminNotificationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Optional<Claim> claimNext(String channel, Instant now) {
    if (!"TELEGRAM".equals(channel) && !"EMAIL".equals(channel)) {
      throw new IllegalArgumentException("Invalid notification channel");
    }

    jdbc.update("""
      UPDATE admin_notifications
      SET status = 'FAILED',
          claim_token = NULL,
          lease_until = NULL,
          last_error_code = 'LEASE_EXPIRED'
      WHERE channel = ?
        AND status = 'PROCESSING'
        AND lease_until <= ?
        AND attempts >= ?
      """,
      channel,
      Timestamp.from(now),
      MAX_ATTEMPTS
    );

    UUID token = UUID.randomUUID();

    return jdbc.query("""
      WITH candidate AS (
        SELECT id
        FROM admin_notifications
        WHERE channel = ?
          AND attempts < ?
          AND (
            (status = 'PENDING' AND available_at <= ?)
            OR
            (status = 'PROCESSING' AND lease_until <= ?)
          )
        ORDER BY created_at, id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
      )
      UPDATE admin_notifications AS notification
      SET status = 'PROCESSING',
          attempts = notification.attempts + 1,
          claim_token = ?,
          lease_until = ?
      FROM candidate
      WHERE notification.id = candidate.id
      RETURNING notification.id,
                notification.event_type,
                notification.resource_id,
                notification.channel,
                notification.attempts
      """,
      (result, rowNumber) -> new Claim(
        result.getObject("id", UUID.class),
        result.getString("event_type"),
        result.getObject("resource_id", UUID.class),
        result.getString("channel"),
        result.getInt("attempts"),
        token
      ),
      channel,
      MAX_ATTEMPTS,
      Timestamp.from(now),
      Timestamp.from(now),
      token,
      Timestamp.from(now.plusSeconds(LEASE_SECONDS))
    ).stream().findFirst();
  }

  @Transactional
  public boolean complete(Claim claim, Instant now) {
    return jdbc.update("""
      UPDATE admin_notifications
      SET status = 'SENT',
          sent_at = ?,
          claim_token = NULL,
          lease_until = NULL,
          last_error_code = NULL
      WHERE id = ?
        AND status = 'PROCESSING'
        AND claim_token = ?
      """,
      Timestamp.from(now),
      claim.id(),
      claim.token()
    ) == 1;
  }

  @Transactional
  public boolean retry(
    Claim claim,
    Instant availableAt,
    String errorCode
  ) {
    if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
      throw new IllegalArgumentException("Invalid notification error code");
    }

    return jdbc.update("""
      UPDATE admin_notifications
      SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
          available_at = ?,
          claim_token = NULL,
          lease_until = NULL,
          last_error_code = ?
      WHERE id = ?
        AND status = 'PROCESSING'
        AND claim_token = ?
      """,
      MAX_ATTEMPTS,
      Timestamp.from(availableAt),
      errorCode,
      claim.id(),
      claim.token()
    ) == 1;
  }

  @Transactional
  public boolean skip(Claim claim) {
    return finishWithoutDelivery(
      claim,
      "SKIPPED",
      "RESOURCE_NOT_AVAILABLE"
    );
  }

  @Transactional
  public boolean fail(Claim claim, String errorCode) {
    return finishWithoutDelivery(claim, "FAILED", errorCode);
  }

  private boolean finishWithoutDelivery(
    Claim claim,
    String status,
    String errorCode
  ) {
    if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
      throw new IllegalArgumentException("Invalid notification error code");
    }

    return jdbc.update("""
    UPDATE admin_notifications
    SET status = ?,
        claim_token = NULL,
        lease_until = NULL,
        last_error_code = ?
    WHERE id = ?
      AND status = 'PROCESSING'
      AND claim_token = ?
    """,
      status,
      errorCode,
      claim.id(),
      claim.token()
    ) == 1;
  }

  public record Claim(
    UUID id,
    String eventType,
    UUID resourceId,
    String channel,
    int attempts,
    UUID token
  ) {
  }
}
