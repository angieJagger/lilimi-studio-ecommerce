package pl.lilimi.auth.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.lilimi.auth.domain.EmailVerificationToken;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface EmailVerificationTokenRepository
  extends JpaRepository<EmailVerificationToken, UUID> {

  boolean existsByAccountIdAndCreatedAtAfter(
    UUID accountId,
    Instant after
  );

  long countByAccountIdAndCreatedAtAfter(
    UUID accountId,
    Instant after
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
    select token
    from EmailVerificationToken token
    where token.tokenHash = :tokenHash
    """)
  Optional<EmailVerificationToken> findByTokenHashForUpdate(
    @Param("tokenHash") String tokenHash
  );
}
