package pl.lilimi.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

  @GetMapping("/api/auth/csrf")
  public ResponseEntity<Void> csrf(
    CsrfToken csrfToken,
    HttpServletRequest request,
    @RequestParam(defaultValue = "false") boolean checkout
  ) {
    csrfToken.getToken();
    if (checkout) {
      request.getSession(true);
    }

    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
