package pl.lilimi.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.auth.application.EmailVerificationService;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

  private final EmailVerificationService verificationService;

  public EmailVerificationController(
    EmailVerificationService verificationService
  ) {
    this.verificationService = verificationService;
  }

  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verifyEmail(@RequestBody VerifyEmailRequest request) {
    verificationService.verify(request.token());
  }
}
