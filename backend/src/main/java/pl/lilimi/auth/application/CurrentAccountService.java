package pl.lilimi.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CurrentAccountService {

  private final UserAccountRepository accountRepository;

  public CurrentAccountService(UserAccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public UUID findAccountId(Authentication authentication) {
    if (
      authentication == null ||
        !authentication.isAuthenticated() ||
        authentication instanceof AnonymousAuthenticationToken
    ) {
      return null;
    }

    var account = accountRepository.findByEmail(authentication.getName())
      .filter(candidate -> candidate.isEnabled())
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.UNAUTHORIZED
      ));

    return account.getId();
  }
}
