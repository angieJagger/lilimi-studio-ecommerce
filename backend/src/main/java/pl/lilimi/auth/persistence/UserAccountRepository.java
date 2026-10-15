package pl.lilimi.auth.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.lilimi.auth.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
  extends JpaRepository<UserAccount, UUID> {

  Optional<UserAccount> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
  select account
  from UserAccount account
  where account.id = :id
  """)
  Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
  select account
  from UserAccount account
  where account.email = :email
  """)
  Optional<UserAccount> findByEmailForUpdate(
    @Param("email") String email
  );
}
