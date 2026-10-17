package pl.lilimi.payment.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface PaymentAttemptRepository
  extends JpaRepository<PaymentAttempt, UUID> {

  Optional<PaymentAttempt> findByIdempotencyKey(
    UUID idempotencyKey
  );

  Optional<PaymentAttempt> findByStripeCheckoutSessionId(
    String sessionId
  );

  Optional<PaymentAttempt> findByOrderIdAndStatusIn(
    UUID orderId,
    Collection<PaymentAttemptStatus> statuses
  );

  boolean existsByOrderIdAndStatus(
    UUID orderId,
    PaymentAttemptStatus status
  );
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
    select attempt
    from PaymentAttempt attempt
    where attempt.id = :id
    """)
  Optional<PaymentAttempt> findByIdForUpdate(
    @Param("id") UUID id
  );

  @Query(value = """
  SELECT DISTINCT ON (order_id) *
  FROM order_payment_attempts
  WHERE order_id IN (:orderIds)
  ORDER BY
    order_id,
    CASE
      WHEN status = 'succeeded' THEN 0
      WHEN status IN ('created', 'pending') THEN 1
      ELSE 2
    END,
    created_at DESC,
    id DESC
  """, nativeQuery = true)
  List<PaymentAttempt> findPaymentSummaries(
    @Param("orderIds") Collection<UUID> orderIds
  );
}
