package pl.lilimi.customer.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.application.CurrentAccountService;
import pl.lilimi.customer.application.CustomerProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/account/profile")
public class CustomerProfileController {

  private final CurrentAccountService currentAccountService;
  private final CustomerProfileService profileService;

  public CustomerProfileController(
    CurrentAccountService currentAccountService,
    CustomerProfileService profileService
  ) {
    this.currentAccountService = currentAccountService;
    this.profileService = profileService;
  }

  @GetMapping
  public CustomerProfileResponse findProfile(
    Authentication authentication
  ) {
    return profileService.findProfile(
      requireAccountId(authentication)
    );
  }

  @PutMapping
  public CustomerProfileResponse updateProfile(
    Authentication authentication,
    @Valid @RequestBody UpdateCustomerProfileRequest request
  ) {
    return profileService.updateProfile(
      requireAccountId(authentication),
      request
    );
  }

  private UUID requireAccountId(Authentication authentication) {
    UUID accountId = currentAccountService.findAccountId(
      authentication
    );

    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return accountId;
  }
}
