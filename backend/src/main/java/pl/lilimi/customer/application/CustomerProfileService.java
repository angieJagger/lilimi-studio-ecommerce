package pl.lilimi.customer.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.customer.api.CustomerProfileResponse;
import pl.lilimi.customer.api.UpdateCustomerProfileRequest;
import pl.lilimi.customer.domain.CustomerProfile;
import pl.lilimi.customer.persistence.CustomerProfileRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerProfileService {

  private final UserAccountRepository accounts;
  private final CustomerProfileRepository profiles;

  public CustomerProfileService(
    UserAccountRepository accounts,
    CustomerProfileRepository profiles
  ) {
    this.accounts = accounts;
    this.profiles = profiles;
  }

  public CustomerProfileResponse findProfile(UUID accountId) {
    var account = accounts.findById(accountId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.UNAUTHORIZED
      ));

    requireCustomer(account);

    var profile = profiles.findById(accountId).orElse(null);

    return CustomerProfileResponse.from(
      account.getEmail(),
      profile
    );
  }

  @Transactional
  public CustomerProfileResponse updateProfile(
    UUID accountId,
    UpdateCustomerProfileRequest request
  ) {
    var account = accounts.findByIdForUpdate(accountId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.UNAUTHORIZED
      ));

    requireCustomer(account);

    var existing = profiles.findById(accountId);
    var profile = existing.orElseGet(
      () -> new CustomerProfile(accountId)
    );

    boolean versionMatches = existing.isPresent()
      ? request.version() != null
      && request.version().longValue() == profile.getVersion()
      : request.version() == null;

    if (!versionMatches) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "CUSTOMER_PROFILE_VERSION_CONFLICT"
      );
    }

    try {
      profile.updateContact(
        request.firstName(),
        request.lastName(),
        request.phone()
      );

      profile.updateAddress(
        request.addressLine1(),
        request.addressLine2(),
        request.postalCode(),
        request.city(),
        request.countryCode()
      );
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "CUSTOMER_PROFILE_INVALID"
      );
    }

    var saved = profiles.saveAndFlush(profile);

    return CustomerProfileResponse.from(
      account.getEmail(),
      saved
    );
  }

  private static void requireCustomer(UserAccount account) {
    if (!account.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    if (account.getRole() != UserRole.CUSTOMER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    if (
      account.isEmailVerificationRequired()
        && !account.isEmailVerified()
    ) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }
}
