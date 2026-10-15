package pl.lilimi.auth.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.lilimi.auth.domain.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository
  extends JpaRepository<PasswordResetToken, UUID> {

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
    from PasswordResetToken token
    where token.tokenHash = :tokenHash
    """)
  Optional<PasswordResetToken> findByTokenHashForUpdate(
    @Param("tokenHash") String tokenHash
  );
}
