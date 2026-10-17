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
}
