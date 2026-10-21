package pl.lilimi.payment.cancellation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CheckoutExpirationQueue {

  private static final int MAX_ATTEMPTS = 5;

  private static final Duration LEASE_DURATION =
    Duration.ofMinutes(5);

  private final JdbcTemplate jdbc;

  public CheckoutExpirationQueue(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int enqueueForOrder(UUID orderId) {
    return jdbc.update(
      """
      INSERT INTO checkout_expiration_tasks (
        payment_attempt_id
      )
      SELECT id
      FROM order_payment_attempts
      WHERE order_id = ?
        AND status IN ('created', 'pending')
      ON CONFLICT (payment_attempt_id) DO NOTHING
      """,
      orderId
    );
  }

  public void flagPaymentAfterCancellation(
    UUID paymentAttemptId
  ) {
    jdbc.update(
      """
      INSERT INTO checkout_expiration_tasks (
        payment_attempt_id,
        status,
        finished_at,
        last_error_code
      )
      VALUES (
        ?,
        'needs_review',
        CURRENT_TIMESTAMP,
        'PAYMENT_AFTER_CANCELLATION'
      )
      ON CONFLICT (payment_attempt_id) DO UPDATE
      SET status = 'needs_review',
          finished_at = CURRENT_TIMESTAMP,
          lease_token = NULL,
          lease_until = NULL,
          last_error_code = 'PAYMENT_AFTER_CANCELLATION'
      """,
      paymentAttemptId
    );
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Claim> claimNext(Instant now) {
    markExhaustedTasks(now);

    UUID token = UUID.randomUUID();

    var claimed = jdbc.query(
      """
      WITH candidate AS (
        SELECT payment_attempt_id
        FROM checkout_expiration_tasks
        WHERE status = 'pending'
          AND attempts < ?
          AND next_attempt_at <= ?
          AND (lease_until IS NULL OR lease_until <= ?)
        ORDER BY
          next_attempt_at,
          created_at,
          payment_attempt_id
        LIMIT 1
        FOR UPDATE SKIP LOCKED
      )
      UPDATE checkout_expiration_tasks task
      SET lease_token = ?,
          lease_until = ?,
          attempts = task.attempts + 1
      FROM candidate
      WHERE task.payment_attempt_id =
        candidate.payment_attempt_id
      RETURNING task.payment_attempt_id, task.attempts
      """,
      (result, row) -> new Claim(
        result.getObject(
          "payment_attempt_id",
          UUID.class
        ),
        token,
        result.getInt("attempts")
      ),
      MAX_ATTEMPTS,
      Timestamp.from(now),
      Timestamp.from(now),
      token,
      Timestamp.from(now.plus(LEASE_DURATION))
    );

    return claimed.stream().findFirst();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean complete(Claim claim, Instant now) {
    return finish(
      claim,
      now,
      "completed",
      null
    );
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean needsReview(
    Claim claim,
    Instant now,
    String errorCode
  ) {
    requireErrorCode(errorCode);

    return finish(
      claim,
      now,
      "needs_review",
      errorCode
    );
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean retry(
    Claim claim,
    Instant now,
    String errorCode
  ) {
    requireErrorCode(errorCode);

    if (claim.attemptNumber() >= MAX_ATTEMPTS) {
      return finish(
        claim,
        now,
        "needs_review",
        errorCode
      );
    }

    Instant nextAttempt = now.plus(
      retryDelay(claim.attemptNumber())
    );

    return jdbc.update(
      """
      UPDATE checkout_expiration_tasks
      SET next_attempt_at = ?,
          lease_token = NULL,
          lease_until = NULL,
          last_error_code = ?
      WHERE payment_attempt_id = ?
        AND status = 'pending'
        AND lease_token = ?
        AND lease_until > ?
      """,
      Timestamp.from(nextAttempt),
      errorCode,
      claim.paymentAttemptId(),
      claim.leaseToken(),
      Timestamp.from(now)
    ) == 1;
  }

  @Transactional(readOnly = true)
  public Map<UUID, CheckoutExpirationSummary> findSummaries(
    Collection<UUID> orderIds
  ) {
    if (orderIds.isEmpty()) {
      return Map.of();
    }

    var summaries =
      new HashMap<UUID, CheckoutExpirationSummary>();

    for (UUID orderId : orderIds) {
      summaries.put(
        orderId,
        new CheckoutExpirationSummary("none", null)
      );
    }

    String placeholders = String.join(
      ",",
      Collections.nCopies(orderIds.size(), "?")
    );

    jdbc.query(
      """
      SELECT DISTINCT ON (attempt.order_id)
        attempt.order_id,
        task.status,
        task.last_error_code
      FROM checkout_expiration_tasks task
      JOIN order_payment_attempts attempt
        ON attempt.id = task.payment_attempt_id
      WHERE attempt.order_id IN (%s)
      ORDER BY
        attempt.order_id,
        CASE task.status
          WHEN 'needs_review' THEN 0
          WHEN 'pending' THEN 1
          ELSE 2
        END,
        task.created_at DESC,
        task.payment_attempt_id DESC
      """.formatted(placeholders),
      (result, row) -> {
        UUID orderId = result.getObject(
          "order_id",
          UUID.class
        );

        String status = result.getString("status");

        summaries.put(
          orderId,
          new CheckoutExpirationSummary(
            status,
            "needs_review".equals(status)
              ? result.getString("last_error_code")
              : null
          )
        );

        return orderId;
      },
      orderIds.toArray()
    );

    return Map.copyOf(summaries);
  }

  private void markExhaustedTasks(Instant now) {
    jdbc.update(
      """
      UPDATE checkout_expiration_tasks
      SET status = 'needs_review',
          finished_at = ?,
          lease_token = NULL,
          lease_until = NULL,
          last_error_code = COALESCE(
            last_error_code,
            'EXPIRATION_ATTEMPTS_EXHAUSTED'
          )
      WHERE status = 'pending'
        AND attempts >= ?
        AND next_attempt_at <= ?
        AND (lease_until IS NULL OR lease_until <= ?)
      """,
      Timestamp.from(now),
      MAX_ATTEMPTS,
      Timestamp.from(now),
      Timestamp.from(now)
    );
  }

  private boolean finish(
    Claim claim,
    Instant now,
    String status,
    String errorCode
  ) {
    return jdbc.update(
      """
      UPDATE checkout_expiration_tasks
      SET status = ?,
          finished_at = ?,
          lease_token = NULL,
          lease_until = NULL,
          last_error_code = ?
      WHERE payment_attempt_id = ?
        AND status = 'pending'
        AND lease_token = ?
        AND lease_until > ?
      """,
      status,
      Timestamp.from(now),
      errorCode,
      claim.paymentAttemptId(),
      claim.leaseToken(),
      Timestamp.from(now)
    ) == 1;
  }

  private static Duration retryDelay(int attemptNumber) {
    return switch (attemptNumber) {
      case 1 -> Duration.ofSeconds(10);
      case 2 -> Duration.ofSeconds(30);
      case 3 -> Duration.ofMinutes(2);
      default -> Duration.ofMinutes(10);
    };
  }

  private static void requireErrorCode(String value) {
    if (
      value == null
        || !value.matches("[A-Z0-9_]{1,64}")
    ) {
      throw new IllegalArgumentException(
        "A short internal error code is required"
      );
    }
  }

  public record Claim(
    UUID paymentAttemptId,
    UUID leaseToken,
    int attemptNumber
  ) {
  }
}
