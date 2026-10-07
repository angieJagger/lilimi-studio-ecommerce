package pl.lilimi.auth.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

  @GetMapping("/api/auth/csrf")
  public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
    csrfToken.getToken();

    return ResponseEntity.noContent().build();
  }
}
