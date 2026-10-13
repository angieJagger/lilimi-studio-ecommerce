package pl.lilimi.auth.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.auth.application.CustomerRegistrationService;

@RestController
@RequestMapping("/api/auth")
public class CustomerRegistrationController {

  private final CustomerRegistrationService registrationService;

  public CustomerRegistrationController(
    CustomerRegistrationService registrationService
  ) {
    this.registrationService = registrationService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public void register(
    @Valid @RequestBody RegisterCustomerRequest request
  ) {
    registrationService.register(request);
  }
}
