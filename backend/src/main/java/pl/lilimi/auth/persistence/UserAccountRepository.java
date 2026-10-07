package pl.lilimi.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lilimi.auth.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository
  extends JpaRepository<UserAccount, UUID> {

  Optional<UserAccount> findByEmail(String email);
}
