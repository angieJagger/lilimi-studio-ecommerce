package pl.lilimi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.application.AccountUserDetailsService;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class AccountUserDetailsServiceTest {

  @Autowired
  private AccountUserDetailsService userDetailsService;

  @Autowired
  private UserAccountRepository accountRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void shouldLoadAdministratorWithStoredPasswordHash() {
    var account = createAdmin();

    var details = userDetailsService.loadUserByUsername(
      "admin-test@example.com"
    );

    assertThat(details.getUsername())
      .isEqualTo("admin-test@example.com");
    assertThat(details.getPassword())
      .isEqualTo(account.getPasswordHash());
    assertThat(passwordEncoder.matches(
      "Test-only-password-2026!",
      details.getPassword()
    )).isTrue();

    assertThat(details.getAuthorities())
      .extracting(GrantedAuthority::getAuthority)
      .containsExactly("ROLE_ADMIN");

    assertThat(details.isEnabled()).isTrue();
  }

  @Test
  void shouldNormalizeEmailBeforeLookup() {
    createAdmin();

    var details = userDetailsService.loadUserByUsername(
      "  ADMIN-TEST@EXAMPLE.COM  "
    );

    assertThat(details.getUsername())
      .isEqualTo("admin-test@example.com");
  }

  @Test
  void shouldExposeDisabledAccountAsDisabled() {
    var account = createAdmin();
    account.disable();

    accountRepository.saveAndFlush(account);

    var details = userDetailsService.loadUserByUsername(
      "admin-test@example.com"
    );

    assertThat(details.isEnabled()).isFalse();
  }

  @Test
  void shouldRejectUnknownAccount() {
    assertThatThrownBy(() ->
      userDetailsService.loadUserByUsername("missing@example.com")
    ).isInstanceOf(UsernameNotFoundException.class);
  }

  private UserAccount createAdmin() {
    var account = new UserAccount(
      "admin-test@example.com",
      passwordEncoder.encode("Test-only-password-2026!"),
      UserRole.ADMIN
    );

    return accountRepository.saveAndFlush(account);
  }
}
