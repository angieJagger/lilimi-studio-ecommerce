package pl.lilimi.auth.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.api.RegisterCustomerRequest;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.util.Locale;

@Service
public class CustomerRegistrationService {

  private final UserAccountRepository accounts;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService verificationService;
  private final EmailVerificationMailService mailService;

  public CustomerRegistrationService(
    UserAccountRepository accounts,
    PasswordEncoder passwordEncoder,
    EmailVerificationService verificationService,
    EmailVerificationMailService mailService
  ) {
    this.accounts = accounts;
    this.passwordEncoder = passwordEncoder;
    this.verificationService = verificationService;
    this.mailService = mailService;
  }

  @Transactional
  public void register(RegisterCustomerRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);

    if (accounts.findByEmail(email).isPresent()) {
      throw emailAlreadyRegistered();
    }

    var account = new UserAccount(
      email,
      passwordEncoder.encode(request.password()),
      UserRole.CUSTOMER
    );

    account.requireEmailVerification();

    try {
      accounts.saveAndFlush(account);
    } catch (DataIntegrityViolationException exception) {
      if (isEmailConflict(exception)) {
        throw emailAlreadyRegistered();
      }

      throw exception;
    }

    String token = verificationService.issueToken(account.getId());

    mailService.send(
      account.getEmail(),
      token,
      request.resolvedLanguage()
    );
  }

  private ResponseStatusException emailAlreadyRegistered() {
    return new ResponseStatusException(
      HttpStatus.CONFLICT,
      "EMAIL_ALREADY_REGISTERED"
    );
  }

  private boolean isEmailConflict(Throwable exception) {
    Throwable cause = exception;

    while (cause != null) {
      if (
        cause instanceof
          org.hibernate.exception.ConstraintViolationException violation
      ) {
        return "uq_user_accounts_email".equals(
          violation.getConstraintName()
        );
      }

      cause = cause.getCause();
    }

    return false;
  }
}
