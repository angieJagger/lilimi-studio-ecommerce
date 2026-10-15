package pl.lilimi.auth.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.PasswordResetTokenRepository;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

@Service
public class PasswordResetRequestService {

  private static final Duration COOLDOWN = Duration.ofMinutes(1);
  private static final Duration DAILY_WINDOW = Duration.ofHours(24);
  private static final long MAX_DAILY_MESSAGES = 5;

  private final UserAccountRepository accounts;
  private final PasswordResetTokenRepository tokens;
  private final PasswordResetService resetService;
  private final PasswordResetMailService mailService;
  private final Clock clock;

  public PasswordResetRequestService(
    UserAccountRepository accounts,
    PasswordResetTokenRepository tokens,
    PasswordResetService resetService,
    PasswordResetMailService mailService,
    Clock clock
  ) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.resetService = resetService;
    this.mailService = mailService;
    this.clock = clock;
  }

  @Transactional
  public void requestReset(String email, String language) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    var account = accounts.findByEmailForUpdate(normalizedEmail)
      .orElse(null);

    if (
      account == null ||
        !account.isEnabled() ||
        account.getRole() != UserRole.CUSTOMER ||
        (
          account.isEmailVerificationRequired() &&
            !account.isEmailVerified()
        )
    ) {
      return;
    }

    var now = clock.instant();

    if (tokens.existsByAccountIdAndCreatedAtAfter(
      account.getId(),
      now.minus(COOLDOWN)
    )) {
      return;
    }

    if (tokens.countByAccountIdAndCreatedAtAfter(
      account.getId(),
      now.minus(DAILY_WINDOW)
    ) >= MAX_DAILY_MESSAGES) {
      return;
    }

    String token = resetService.issueToken(account.getId());

    mailService.send(
      account.getEmail(),
      token,
      language == null ? "en" : language
    );
  }
}
