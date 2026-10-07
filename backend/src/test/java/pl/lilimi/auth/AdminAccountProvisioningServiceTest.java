package pl.lilimi.auth;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.application.AdminAccountProvisioningService;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminAccountProvisioningServiceTest {

  @Autowired
  private AdminAccountProvisioningService provisioning;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void shouldCreateEnabledAdminWithEncodedPassword() {
    String email = "provision-admin@example.com";
    String password = "Test-only-password-2026!";

    provisioning.createAdmin(email, password);

    var account = accounts.findByEmail(email).orElseThrow();

    assertThat(account.getId()).isNotNull();
    assertThat(account.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.getPasswordHash()).isNotEqualTo(password);

    assertThat(
      passwordEncoder.matches(password, account.getPasswordHash())
    ).isTrue();
  }

  @Test
  void shouldNormalizeEmailCase() {
    provisioning.createAdmin(
      "PROVISION-ADMIN@example.com",
      "Test-only-password-2026!"
    );

    var account = accounts.findByEmail(
      "provision-admin@example.com"
    ).orElseThrow();

    assertThat(account.getEmail())
      .isEqualTo("provision-admin@example.com");
  }

  @Test
  void shouldRejectExistingAccountWithoutChangingItsRoleOrPassword() {
    String email = "existing-customer@example.com";
    String originalHash = passwordEncoder.encode(
      "Original-test-password!"
    );

    var original = accounts.saveAndFlush(
      new UserAccount(email, originalHash, UserRole.CUSTOMER)
    );

    assertThatThrownBy(() ->
      provisioning.createAdmin(email, "Different-test-password!")
    ).isInstanceOf(IllegalStateException.class)
      .hasMessage("An account with this email already exists");

    var remaining = accounts.findByEmail(email).orElseThrow();

    assertThat(remaining.getId()).isEqualTo(original.getId());
    assertThat(remaining.getRole()).isEqualTo(UserRole.CUSTOMER);
    assertThat(remaining.getPasswordHash()).isEqualTo(originalHash);
  }

  @Test
  void shouldRejectInvalidEmail() {
    long initialCount = accounts.count();

    assertThatThrownBy(() ->
      provisioning.createAdmin(
        "invalid-email",
        "Test-only-password-2026!"
      )
    ).isInstanceOf(ConstraintViolationException.class);

    assertThat(accounts.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectShortPassword() {
    long initialCount = accounts.count();

    assertThatThrownBy(() ->
      provisioning.createAdmin(
        "provision-admin@example.com",
        "short"
      )
    ).isInstanceOf(ConstraintViolationException.class);

    assertThat(accounts.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectPasswordExceedingBcryptByteLimit() {
    long initialCount = accounts.count();

    String password = "ą".repeat(37);

    assertThatThrownBy(() ->
      provisioning.createAdmin(
        "provision-admin@example.com",
        password
      )
    ).isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
        "Administrator password must not exceed 72 UTF-8 bytes"
      );

    assertThat(accounts.count()).isEqualTo(initialCount);
  }
}
