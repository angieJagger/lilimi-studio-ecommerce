package pl.lilimi.auth.application;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AccountUserDetailsService implements UserDetailsService {

  private final UserAccountRepository accountRepository;

  public AccountUserDetailsService(
    UserAccountRepository accountRepository
  ) {
    this.accountRepository = accountRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new UsernameNotFoundException("Account not found");
    }

    String email = username.trim().toLowerCase(Locale.ROOT);

    var account = accountRepository.findByEmail(email)
      .orElseThrow(() ->
        new UsernameNotFoundException("Account not found")
      );

    return User.withUsername(account.getEmail())
      .password(account.getPasswordHash())
      .roles(account.getRole().name())
      .disabled(!account.isEnabled())
      .build();
  }
}
