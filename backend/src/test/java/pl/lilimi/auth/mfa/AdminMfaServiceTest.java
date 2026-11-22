package pl.lilimi.auth.mfa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMfaServiceTest {

  @Mock
  private UserAccountRepository accounts;

  @Mock
  private AdminMfaRepository repository;

  @Mock
  private MfaSecretCipher cipher;

  @Mock
  private MfaTotpService totp;

  @Mock
  private MfaRecoveryCodeService recoveryCodes;

  @InjectMocks
  private AdminMfaService service;

  private final UUID accountId = UUID.randomUUID();

  @Test
  void shouldActivateAndStoreOnlyRecoveryCodeHash() {
    allowAdmin();

    when(repository.lock(accountId)).thenReturn(Optional.of(
      new AdminMfaRepository.MfaState("encrypted", null, null)
    ));
    when(cipher.decrypt("encrypted")).thenReturn("secret");
    when(totp.matchingTimeStep(
      eq("secret"), eq("123456"), any(Instant.class)
    )).thenReturn(OptionalLong.of(100));
    when(repository.enable(accountId, 100)).thenReturn(true);
    when(recoveryCodes.generate()).thenReturn(List.of(
      new MfaRecoveryCodeService.RecoveryCode("visible-code", "hash")
    ));

    assertThat(service.activate(accountId, "123456"))
      .containsExactly("visible-code");

    verify(repository).addRecoveryCode(accountId, "hash");
    verify(repository, never()).addRecoveryCode(
      accountId, "visible-code"
    );
  }

  @Test
  void shouldNotActivateForInvalidCode() {
    allowAdmin();

    when(repository.lock(accountId)).thenReturn(Optional.of(
      new AdminMfaRepository.MfaState("encrypted", null, null)
    ));
    when(cipher.decrypt("encrypted")).thenReturn("secret");
    when(totp.matchingTimeStep(
      eq("secret"), eq("000000"), any(Instant.class)
    )).thenReturn(OptionalLong.empty());

    assertThatThrownBy(() -> service.activate(accountId, "000000"))
      .isInstanceOf(ResponseStatusException.class);

    verify(repository, never()).enable(any(), anyLong());
    verifyNoInteractions(recoveryCodes);
  }

  @Test
  void shouldRejectAlreadyEnabledSetupWithoutRevealingSecret() {
    allowAdmin();
    enabledState();

    assertThatThrownBy(() -> service.beginSetup(accountId))
      .isInstanceOf(ResponseStatusException.class);

    verifyNoInteractions(cipher, recoveryCodes);
  }

  @Test
  void shouldRejectTotpWhenDatabaseRejectsItsTimeStep() {
    allowAdmin();
    enabledState();

    when(cipher.decrypt("encrypted")).thenReturn("secret");
    when(totp.matchingTimeStep(
      eq("secret"), eq("123456"), any(Instant.class)
    )).thenReturn(OptionalLong.of(100));
    when(repository.consumeTimeStep(accountId, 100))
      .thenReturn(false);

    assertThat(service.verifyTotp(accountId, "123456")).isFalse();
    verify(repository).consumeTimeStep(accountId, 100);
  }

  @Test
  void shouldConsumeMatchingRecoveryCode() {
    allowAdmin();
    enabledState();

    UUID codeId = UUID.randomUUID();
    when(repository.lockUnusedRecoveryCodes(accountId))
      .thenReturn(List.of(
        new AdminMfaRepository.StoredRecoveryCode(codeId, "hash")
      ));
    when(recoveryCodes.matches("submitted", "hash"))
      .thenReturn(true);
    when(repository.consumeRecoveryCode(accountId, codeId))
      .thenReturn(true);

    assertThat(service.verifyRecoveryCode(accountId, "submitted"))
      .isTrue();

    verify(repository).consumeRecoveryCode(accountId, codeId);
  }

  @Test
  void shouldRejectCustomerBeforeAccessingMfaData() {
    when(accounts.findById(accountId)).thenReturn(Optional.of(
      new UserAccount(
        "customer@example.com",
        "unused-password-hash",
        UserRole.CUSTOMER
      )
    ));

    assertThatThrownBy(() -> service.beginSetup(accountId))
      .isInstanceOf(ResponseStatusException.class);

    verifyNoInteractions(repository, cipher, totp, recoveryCodes);
  }

  private void allowAdmin() {
    when(accounts.findById(accountId)).thenReturn(Optional.of(
      new UserAccount(
        "admin@example.com",
        "unused-password-hash",
        UserRole.ADMIN
      )
    ));
  }

  private void enabledState() {
    when(repository.lock(accountId)).thenReturn(Optional.of(
      new AdminMfaRepository.MfaState(
        "encrypted",
        Instant.parse("2026-01-01T00:00:00Z"),
        99L
      )
    ));
  }
}
