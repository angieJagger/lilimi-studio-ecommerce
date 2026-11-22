package pl.lilimi.auth.mfa;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminMfaService {

  private final UserAccountRepository accounts;
  private final AdminMfaRepository repository;
  private final MfaSecretCipher cipher;
  private final MfaTotpService totp;
  private final MfaRecoveryCodeService recoveryCodes;

  public AdminMfaService(
    UserAccountRepository accounts,
    AdminMfaRepository repository,
    MfaSecretCipher cipher,
    MfaTotpService totp,
    MfaRecoveryCodeService recoveryCodes
  ) {
    this.accounts = accounts;
    this.repository = repository;
    this.cipher = cipher;
    this.totp = totp;
    this.recoveryCodes = recoveryCodes;
  }

  public boolean isEnabled(UUID accountId) {
    requireAdmin(accountId);

    return repository.lock(accountId)
      .map(AdminMfaRepository.MfaState::enabled)
      .orElse(false);
  }

  public String beginSetup(UUID accountId) {
    requireAdmin(accountId);

    var existing = repository.lock(accountId);

    if (existing.isEmpty()) {
      repository.createPending(
        accountId,
        cipher.encrypt(totp.generateSecret())
      );
    }

    var state = repository.lock(accountId).orElseThrow();

    if (state.enabled()) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "MFA is already enabled"
      );
    }

    return cipher.decrypt(state.encryptedSecret());
  }

  public List<String> activate(UUID accountId, String code) {
    requireAdmin(accountId);

    var state = repository.lock(accountId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Start MFA setup first"
      ));

    if (state.enabled()) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "MFA is already enabled"
      );
    }

    var step = totp.matchingTimeStep(
      cipher.decrypt(state.encryptedSecret()),
      code,
      Instant.now()
    );

    if (step.isEmpty()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid MFA code"
      );
    }

    if (!repository.enable(accountId, step.getAsLong())) {
      throw new IllegalStateException("Could not enable MFA");
    }

    var generated = recoveryCodes.generate();

    for (var recoveryCode : generated) {
      repository.addRecoveryCode(accountId, recoveryCode.hash());
    }

    return generated.stream()
      .map(MfaRecoveryCodeService.RecoveryCode::value)
      .toList();
  }

  public boolean verifyTotp(UUID accountId, String code) {
    requireAdmin(accountId);

    var state = repository.lock(accountId);

    if (state.isEmpty() || !state.get().enabled()) {
      return false;
    }

    var step = totp.matchingTimeStep(
      cipher.decrypt(state.get().encryptedSecret()),
      code,
      Instant.now()
    );

    return step.isPresent()
      && repository.consumeTimeStep(accountId, step.getAsLong());
  }

  public boolean verifyRecoveryCode(UUID accountId, String code) {
    requireAdmin(accountId);

    var state = repository.lock(accountId);

    if (state.isEmpty() || !state.get().enabled()) {
      return false;
    }

    for (var stored : repository.lockUnusedRecoveryCodes(accountId)) {
      if (recoveryCodes.matches(code, stored.hash())) {
        return repository.consumeRecoveryCode(accountId, stored.id());
      }
    }

    return false;
  }

  private void requireAdmin(UUID accountId) {
    var account = accounts.findById(accountId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.FORBIDDEN
      ));

    if (!account.isEnabled() || account.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }
}
