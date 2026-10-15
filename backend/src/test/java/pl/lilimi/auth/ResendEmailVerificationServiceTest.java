package pl.lilimi.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.lilimi.auth.application.EmailVerificationMailService;
import pl.lilimi.auth.application.EmailVerificationService;
import pl.lilimi.auth.application.ResendEmailVerificationService;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.EmailVerificationTokenRepository;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendEmailVerificationServiceTest {

  private static final String EMAIL = "customer@example.com";
  private static final Instant NOW =
    Instant.parse("2026-10-15T10:00:00Z");

  @Mock
  private UserAccountRepository accounts;

  @Mock
  private EmailVerificationTokenRepository tokens;

  @Mock
  private EmailVerificationService verificationService;

  @Mock
  private EmailVerificationMailService mailService;

  private ResendEmailVerificationService service;

  @BeforeEach
  void setUp() {
    service = new ResendEmailVerificationService(
      accounts,
      tokens,
      verificationService,
      mailService,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void shouldIgnoreUnknownEmail() {
    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.empty());

    service.resend(EMAIL, "pl");

    verifyNoInteractions(tokens, verificationService, mailService);
  }

  @Test
  void shouldIgnoreVerifiedAccount() {
    var account = pendingAccount();
    account.verifyEmail();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    service.resend(EMAIL, "pl");

    verifyNoInteractions(tokens, verificationService, mailService);
  }

  @Test
  void shouldIgnoreDisabledAccount() {
    var account = pendingAccount();
    account.disable();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    service.resend(EMAIL, "pl");

    verifyNoInteractions(tokens, verificationService, mailService);
  }

  @Test
  void shouldIgnoreAdministratorAccount() {
    var account = new UserAccount(
      EMAIL,
      "{noop}unused-test-password",
      UserRole.ADMIN
    );

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    service.resend(EMAIL, "pl");

    verifyNoInteractions(tokens, verificationService, mailService);
  }

  @Test
  void shouldNotSendDuringCooldown() {
    var account = pendingAccount();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(tokens.existsByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(60)
    )).thenReturn(true);

    service.resend(EMAIL, "pl");

    verify(tokens, never())
      .countByAccountIdAndCreatedAtAfter(any(), any());

    verifyNoInteractions(verificationService, mailService);
  }

  @Test
  void shouldNotSendWhenDailyLimitIsReached() {
    var account = pendingAccount();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(tokens.countByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(86400)
    )).thenReturn(5L);

    service.resend(EMAIL, "pl");

    verifyNoInteractions(verificationService, mailService);
  }

  @Test
  void shouldNormalizeEmailAndSendPolishMessageWithinLimits() {
    var account = pendingAccount();
    String token = "a".repeat(43);

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(tokens.countByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(86400)
    )).thenReturn(4L);

    when(verificationService.issueToken(account.getId()))
      .thenReturn(token);

    service.resend("  Customer@Example.com  ", "pl");

    verify(accounts).findByEmailForUpdate(EMAIL);

    verify(tokens).existsByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(60)
    );

    verify(verificationService).issueToken(account.getId());
    verify(mailService).send(EMAIL, token, "pl");
  }

  @Test
  void shouldDefaultToEnglishWhenLanguageIsMissing() {
    var account = pendingAccount();
    String token = "b".repeat(43);

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(verificationService.issueToken(account.getId()))
      .thenReturn(token);

    service.resend(EMAIL, null);

    verify(mailService).send(EMAIL, token, "en");
  }

  private UserAccount pendingAccount() {
    var account = new UserAccount(
      EMAIL,
      "{noop}unused-test-password",
      UserRole.CUSTOMER
    );

    account.requireEmailVerification();

    ReflectionTestUtils.setField(
      account,
      "id",
      UUID.randomUUID()
    );

    return account;
  }
}
