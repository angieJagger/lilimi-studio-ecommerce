package pl.lilimi.auth.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.auth.application.CurrentAccountService;
import pl.lilimi.customer.application.CustomerProfileService;

@RestController
@RequestMapping("/api/auth")
public class SessionController {

  private final CurrentAccountService currentAccountService;
  private final CustomerProfileService profileService;

  public SessionController(
    CurrentAccountService currentAccountService,
    CustomerProfileService profileService
  ) {
    this.currentAccountService = currentAccountService;
    this.profileService = profileService;
  }

  @GetMapping("/me")
  public CurrentUserResponse currentUser(Authentication authentication) {
    var roles = authentication.getAuthorities()
      .stream()
      .map(authority -> authority.getAuthority())
      .filter(authority -> authority.startsWith("ROLE_"))
      .map(authority -> authority.substring("ROLE_".length()))
      .sorted()
      .toList();

    String firstName = null;

    if (roles.contains("CUSTOMER") && !roles.contains("ADMIN")) {
      var accountId = currentAccountService.findAccountId(authentication);

      if (accountId != null) {
        firstName = profileService.findProfile(accountId).firstName();
      }
    }

    return new CurrentUserResponse(
      authentication.getName(),
      roles,
      firstName
    );
  }
}
