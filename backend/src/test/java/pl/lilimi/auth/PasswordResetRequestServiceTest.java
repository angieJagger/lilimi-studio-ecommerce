package pl.lilimi.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.lilimi.auth.application.PasswordResetMailService;
import pl.lilimi.auth.application.PasswordResetService;
import pl.lilimi.auth.application.PasswordResetRequestService;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.PasswordResetTokenRepository;
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
class PasswordResetRequestServiceTest {

  private static final String EMAIL = "customer@example.com";
  private static final Instant NOW =
    Instant.parse("2026-10-15T10:00:00Z");

  @Mock
  private UserAccountRepository accounts;

  @Mock
  private PasswordResetTokenRepository tokens;

  @Mock
  private PasswordResetService resetService;

  @Mock
  private PasswordResetMailService mailService;

  private PasswordResetRequestService service;

  @BeforeEach
  void setUp() {
    service = new PasswordResetRequestService(
      accounts,
      tokens,
      resetService,
      mailService,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void shouldIgnoreUnknownEmail() {
    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.empty());

    service.requestReset(EMAIL, "pl");

    verifyNoInteractions(tokens, resetService, mailService);
  }

  @Test
  void shouldIgnoreUnverifiedAccount() {
    var account = verifiedAccount();
    account.requireEmailVerification();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    service.requestReset(EMAIL, "pl");

    verifyNoInteractions(tokens, resetService, mailService);
  }

  @Test
  void shouldIgnoreDisabledAccount() {
    var account = verifiedAccount();
    account.disable();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    service.requestReset(EMAIL, "pl");

    verifyNoInteractions(tokens, resetService, mailService);
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

    service.requestReset(EMAIL, "pl");

    verifyNoInteractions(tokens, resetService, mailService);
  }

  @Test
  void shouldNotSendDuringCooldown() {
    var account = verifiedAccount();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(tokens.existsByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(60)
    )).thenReturn(true);

    service.requestReset(EMAIL, "pl");

    verify(tokens, never())
      .countByAccountIdAndCreatedAtAfter(any(), any());

    verifyNoInteractions(resetService, mailService);
  }

  @Test
  void shouldNotSendWhenDailyLimitIsReached() {
    var account = verifiedAccount();

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(tokens.countByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(86400)
    )).thenReturn(5L);

    service.requestReset(EMAIL, "pl");

    verifyNoInteractions(resetService, mailService);
  }

  @Test
  void shouldNormalizeEmailAndSendPolishMessageWithinLimits() {
    var account = verifiedAccount();
    String token = "a".repeat(43);

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(tokens.countByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(86400)
    )).thenReturn(4L);

    when(resetService.issueToken(account.getId()))
      .thenReturn(token);

    service.requestReset("  Customer@Example.com  ", "pl");

    verify(accounts).findByEmailForUpdate(EMAIL);

    verify(tokens).existsByAccountIdAndCreatedAtAfter(
      account.getId(),
      NOW.minusSeconds(60)
    );

    verify(resetService).issueToken(account.getId());
    verify(mailService).send(EMAIL, token, "pl");
  }

  @Test
  void shouldDefaultToEnglishWhenLanguageIsMissing() {
    var account = verifiedAccount();
    String token = "b".repeat(43);

    when(accounts.findByEmailForUpdate(EMAIL))
      .thenReturn(Optional.of(account));

    when(resetService.issueToken(account.getId()))
      .thenReturn(token);

    service.requestReset(EMAIL, null);

    verify(mailService).send(EMAIL, token, "en");
  }

  private UserAccount verifiedAccount() {
    var account = new UserAccount(
      EMAIL,
      "{noop}unused-test-password",
      UserRole.CUSTOMER
    );

    account.requireEmailVerification();
    account.verifyEmail();

    ReflectionTestUtils.setField(
      account,
      "id",
      UUID.randomUUID()
    );

    return account;
  }
}
