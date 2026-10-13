package pl.lilimi.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
  assignableTypes = CustomerRegistrationController.class
)
public class CustomerRegistrationExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<RegistrationError> handleValidation(
    MethodArgumentNotValidException exception
  ) {
    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(new RegistrationError("INVALID_REGISTRATION_DATA"));
  }

  public record RegistrationError(String code) {}
}
