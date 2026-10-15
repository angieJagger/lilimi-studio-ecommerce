package pl.lilimi.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.domain.PasswordResetToken;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.PasswordResetTokenRepository;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PasswordResetService {

  private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

  private final PasswordResetTokenRepository tokens;
  private final UserAccountRepository accounts;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public PasswordResetService(
    PasswordResetTokenRepository tokens,
    UserAccountRepository accounts,
    PasswordEncoder passwordEncoder,
    Clock clock
  ) {
    this.tokens = tokens;
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public String issueToken(UUID accountId) {
    var account = accounts.findByIdForUpdate(accountId)
      .orElseThrow(() -> new IllegalStateException("Account not found"));

    if (!canResetPassword(account)) {
      throw new IllegalStateException(
        "Account is not eligible for password reset"
      );
    }

    byte[] bytes = new byte[32];
    random.nextBytes(bytes);

    String rawToken = Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(bytes);

    var now = clock.instant();

    tokens.save(new PasswordResetToken(
      account.getId(),
      hash(rawToken),
      account.getCredentialsVersion(),
      now,
      now.plus(TOKEN_VALIDITY)
    ));

    return rawToken;
  }

  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    validatePassword(newPassword);

    if (
      rawToken == null ||
        !rawToken.matches("[A-Za-z0-9_-]{43}")
    ) {
      throw invalidToken();
    }

    var token = tokens.findByTokenHashForUpdate(hash(rawToken))
      .orElseThrow(this::invalidToken);

    var account = accounts.findByIdForUpdate(token.getAccountId())
      .orElseThrow(this::invalidToken);

    var now = clock.instant();

    if (
      !token.isUsableAt(now) ||
        !canResetPassword(account) ||
        token.getCredentialsVersion() != account.getCredentialsVersion()
    ) {
      throw invalidToken();
    }

    String encodedPassword = passwordEncoder.encode(newPassword);

    account.changePasswordHash(encodedPassword);
    token.consume(now);
  }

  private boolean canResetPassword(UserAccount account) {
    return account.isEnabled()
      && account.getRole() == UserRole.CUSTOMER
      && (
      !account.isEmailVerificationRequired() ||
        account.isEmailVerified()
    );
  }

  private void validatePassword(String password) {
    if (
      password == null ||
        password.isBlank() ||
        password.length() < 12 ||
        password.length() > 72 ||
        password.getBytes(StandardCharsets.UTF_8).length > 72
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "PASSWORD_RESET_PASSWORD_INVALID"
      );
    }
  }

  private ResponseStatusException invalidToken() {
    return new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "PASSWORD_RESET_TOKEN_INVALID"
    );
  }

  private String hash(String rawToken) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(rawToken.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
        "SHA-256 is unavailable",
        exception
      );
    }
  }
}
