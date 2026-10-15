package pl.lilimi.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.domain.EmailVerificationToken;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.EmailVerificationTokenRepository;
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
public class EmailVerificationService {

  private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);

  private final EmailVerificationTokenRepository tokens;
  private final UserAccountRepository accounts;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public EmailVerificationService(
    EmailVerificationTokenRepository tokens,
    UserAccountRepository accounts,
    Clock clock
  ) {
    this.tokens = tokens;
    this.accounts = accounts;
    this.clock = clock;
  }

  @Transactional
  public String issueToken(UUID accountId) {
    var account = accounts.findByIdForUpdate(accountId)
      .orElseThrow(() -> new IllegalStateException("Account not found"));

    if (
      !account.isEnabled() ||
        account.getRole() != UserRole.CUSTOMER ||
        !account.isEmailVerificationRequired() ||
        account.isEmailVerified()
    ) {
      throw new IllegalStateException(
        "Account does not require email verification"
      );
    }

    byte[] bytes = new byte[32];
    random.nextBytes(bytes);

    String rawToken = Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(bytes);

    var now = clock.instant();

    tokens.save(new EmailVerificationToken(
      account.getId(),
      hash(rawToken),
      now,
      now.plus(TOKEN_VALIDITY)
    ));

    return rawToken;
  }

  @Transactional
  public void verify(String rawToken) {
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
        !account.isEnabled() ||
        account.getRole() != UserRole.CUSTOMER ||
        !account.isEmailVerificationRequired() ||
        account.isEmailVerified()
    ) {
      throw invalidToken();
    }

    token.consume(now);
    account.verifyEmail();
  }

  private ResponseStatusException invalidToken() {
    return new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "EMAIL_VERIFICATION_TOKEN_INVALID"
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
