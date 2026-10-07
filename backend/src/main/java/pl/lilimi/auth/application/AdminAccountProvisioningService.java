package pl.lilimi.auth.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@Validated
public class AdminAccountProvisioningService {

  private final UserAccountRepository accounts;
  private final PasswordEncoder passwordEncoder;

  public AdminAccountProvisioningService(
    UserAccountRepository accounts,
    PasswordEncoder passwordEncoder
  ) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void createAdmin(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12) String password
  ) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException(
        "Administrator password must not exceed 72 UTF-8 bytes"
      );
    }

    if (accounts.findByEmail(normalizedEmail).isPresent()) {
      throw new IllegalStateException(
        "An account with this email already exists"
      );
    }

    var account = new UserAccount(
      normalizedEmail,
      passwordEncoder.encode(password),
      UserRole.ADMIN
    );

    accounts.saveAndFlush(account);
  }
}
