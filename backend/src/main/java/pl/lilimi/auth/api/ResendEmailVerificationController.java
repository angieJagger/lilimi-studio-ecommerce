package pl.lilimi.auth.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.auth.application.ResendEmailVerificationService;

@RestController
@RequestMapping("/api/auth")
public class ResendEmailVerificationController {

  private final ResendEmailVerificationService service;

  public ResendEmailVerificationController(
    ResendEmailVerificationService service
  ) {
    this.service = service;
  }

  @PostMapping("/resend-verification")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resend(
    @Valid @RequestBody ResendVerificationRequest request
  ) {
    service.resend(request.email(), request.language());
  }
}
