package pl.lilimi.auth.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.application.AccountPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/auth/mfa")
public class AdminMfaController {

  private final AdminMfaService service;
  private final AdminMfaRateLimiter rateLimiter;

  public AdminMfaController(
    AdminMfaService service,
    AdminMfaRateLimiter rateLimiter
  ) {
    this.service = service;
    this.rateLimiter = rateLimiter;
  }

  @GetMapping("/status")
  public ResponseEntity<StatusResponse> status(
    @AuthenticationPrincipal AccountPrincipal principal,
    HttpServletRequest request
  ) {
    requirePrincipal(principal);

    return noStore(new StatusResponse(
      service.isEnabled(principal.getAccountId()),
      AdminMfaSession.isVerified(
        request.getSession(false),
        principal
      )
    ));
  }

  @PostMapping("/setup")
  public ResponseEntity<SetupResponse> setup(
    @AuthenticationPrincipal AccountPrincipal principal
  ) {
    requirePrincipal(principal);
    reserveAttempt(principal);

    return noStore(new SetupResponse(
      service.beginSetup(principal.getAccountId())
    ));
  }

  @PostMapping("/activate")
  public ResponseEntity<ActivationResponse> activate(
    @AuthenticationPrincipal AccountPrincipal principal,
    @Valid @RequestBody CodeRequest body,
    HttpServletRequest request
  ) {
    requirePrincipal(principal);
    HttpSession session = requireSession(request);
    reserveAttempt(principal);

    List<String> recoveryCodes = service.activate(
      principal.getAccountId(),
      body.code()
    );

    request.changeSessionId();
    AdminMfaSession.markVerified(session, principal);

    return noStore(new ActivationResponse(recoveryCodes));
  }

  @PostMapping("/verify")
  public ResponseEntity<Void> verify(
    @AuthenticationPrincipal AccountPrincipal principal,
    @Valid @RequestBody CodeRequest body,
    HttpServletRequest request
  ) {
    requirePrincipal(principal);
    HttpSession session = requireSession(request);
    reserveAttempt(principal);

    if (!service.verifyTotp(principal.getAccountId(), body.code())) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid or already used MFA code"
      );
    }

    request.changeSessionId();
    AdminMfaSession.markVerified(session, principal);

    return ResponseEntity.noContent()
      .cacheControl(CacheControl.noStore())
      .build();
  }

  @PostMapping("/recovery")
  public ResponseEntity<Void> recover(
    @AuthenticationPrincipal AccountPrincipal principal,
    @Valid @RequestBody CodeRequest body,
    HttpServletRequest request
  ) {
    requirePrincipal(principal);
    HttpSession session = requireSession(request);
    reserveAttempt(principal);

    if (!service.verifyRecoveryCode(
      principal.getAccountId(),
      body.code()
    )) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid or already used recovery code"
      );
    }

    request.changeSessionId();
    AdminMfaSession.markVerified(session, principal);

    return ResponseEntity.noContent()
      .cacheControl(CacheControl.noStore())
      .build();
  }

  private void reserveAttempt(AccountPrincipal principal) {
    long retryAfter = rateLimiter.reserveAttempt(
      principal.getAccountId()
    );

    if (retryAfter > 0) {
      throw new ResponseStatusException(
        HttpStatus.TOO_MANY_REQUESTS,
        "Too many MFA attempts"
      );
    }
  }

  private static void requirePrincipal(AccountPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
  }

  private static HttpSession requireSession(
    HttpServletRequest request
  ) {
    HttpSession session = request.getSession(false);

    if (session == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return session;
  }

  private static <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(body);
  }

  public record CodeRequest(
    @NotBlank @Size(max = 64) String code
  ) {
    @Override
    public String toString() {
      return "CodeRequest[REDACTED]";
    }
  }

  public record StatusResponse(
    boolean enabled,
    boolean verified
  ) {
  }

  public record SetupResponse(String secret) {
    @Override
    public String toString() {
      return "SetupResponse[REDACTED]";
    }
  }

  public record ActivationResponse(List<String> recoveryCodes) {
    @Override
    public String toString() {
      return "ActivationResponse[REDACTED]";
    }
  }
}
