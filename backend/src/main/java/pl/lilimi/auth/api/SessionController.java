package pl.lilimi.auth.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class SessionController {

  @GetMapping("/me")
  public CurrentUserResponse currentUser(Authentication authentication) {
    var roles = authentication.getAuthorities()
      .stream()
      .map(authority -> authority.getAuthority())
      .filter(authority -> authority.startsWith("ROLE_"))
      .map(authority -> authority.substring("ROLE_".length()))
      .sorted()
      .toList();

    return new CurrentUserResponse(
      authentication.getName(),
      roles
    );
  }
}
