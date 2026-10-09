package pl.lilimi.inquiry.api.admin;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.lilimi.inquiry.application.admin.InquiryStatusConflictException;

@RestControllerAdvice(
  assignableTypes = AdminInquiryController.class
)
public class AdminInquiryExceptionHandler {

  @ExceptionHandler(InquiryStatusConflictException.class)
  public ProblemDetail handleStatusConflict(
    InquiryStatusConflictException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      exception.getMessage()
    );

    problem.setProperty("code", exception.getCode());

    return problem;
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleConcurrentUpdate(
    OptimisticLockingFailureException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      "Inquiry was modified. Reload its details before updating."
    );

    problem.setProperty("code", "INQUIRY_VERSION_CONFLICT");

    return problem;
  }
}
