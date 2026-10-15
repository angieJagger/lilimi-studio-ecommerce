package pl.lilimi.auth.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.auth.application.PasswordResetRequestService;
import pl.lilimi.auth.application.PasswordResetService;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

  private final PasswordResetRequestService requestService;
  private final PasswordResetService resetService;

  public PasswordResetController(
    PasswordResetRequestService requestService,
    PasswordResetService resetService
  ) {
    this.requestService = requestService;
    this.resetService = resetService;
  }

  @PostMapping("/request-password-reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void requestReset(
    @Valid @RequestBody RequestPasswordResetRequest request
  ) {
    requestService.requestReset(request.email(), request.language());
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@RequestBody ResetPasswordRequest request) {
    resetService.resetPassword(request.token(), request.password());
  }
}
