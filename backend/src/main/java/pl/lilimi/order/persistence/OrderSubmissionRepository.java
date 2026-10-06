package pl.lilimi.order.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OrderSubmissionRepository {

  private final JdbcTemplate jdbcTemplate;

  public OrderSubmissionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void reserve(UUID idempotencyKey, String requestHash) {
    jdbcTemplate.update("""
            INSERT INTO order_submissions (
                idempotency_key,
                request_hash
            )
            VALUES (?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
      idempotencyKey,
      requestHash
    );
  }

  public OrderSubmission lock(UUID idempotencyKey) {
    return jdbcTemplate.queryForObject("""
            SELECT idempotency_key, request_hash, order_id
            FROM order_submissions
            WHERE idempotency_key = ?
            FOR UPDATE
            """,
      (resultSet, rowNumber) -> new OrderSubmission(
        resultSet.getObject("idempotency_key", UUID.class),
        resultSet.getString("request_hash"),
        resultSet.getObject("order_id", UUID.class)
      ),
      idempotencyKey
    );
  }

  public void attachOrder(UUID idempotencyKey, UUID orderId) {
    int updated = jdbcTemplate.update("""
            UPDATE order_submissions
            SET order_id = ?
            WHERE idempotency_key = ?
              AND order_id IS NULL
            """,
      orderId,
      idempotencyKey
    );

    if (updated != 1) {
      throw new IllegalStateException(
        "Could not attach order to submission"
      );
    }
  }
}
