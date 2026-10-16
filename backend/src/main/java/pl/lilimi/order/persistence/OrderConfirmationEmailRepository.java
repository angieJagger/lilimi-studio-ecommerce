package pl.lilimi.order.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OrderConfirmationEmailRepository {

  private final JdbcTemplate jdbcTemplate;

  public OrderConfirmationEmailRepository(
    JdbcTemplate jdbcTemplate
  ) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void enqueue(UUID orderId) {
    Objects.requireNonNull(orderId, "Order ID is required");

    jdbcTemplate.update("""
        INSERT INTO order_confirmation_emails (order_id)
        VALUES (?)
        ON CONFLICT (order_id) DO NOTHING
        """,
      orderId
    );
  }

  public Optional<PendingEmail> lockNext(Instant now) {
    var entries = jdbcTemplate.query("""
        SELECT order_id, attempts
        FROM order_confirmation_emails
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
    );

    return entries.stream().findFirst();
  }

  public void markSent(UUID orderId, Instant sentAt) {
    int updated = jdbcTemplate.update("""
        UPDATE order_confirmation_emails
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

  public void recordFailure(
    UUID orderId,
    boolean retry,
    Instant nextAttemptAt,
    String errorCode
  ) {
    int updated = jdbcTemplate.update("""
        UPDATE order_confirmation_emails
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
        "Could not update order confirmation queue entry"
      );
    }
  }

  public record PendingEmail(
    UUID orderId,
    int attempts
  ) {
  }
}
