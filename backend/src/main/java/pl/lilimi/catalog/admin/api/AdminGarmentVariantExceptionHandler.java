package pl.lilimi.catalog.admin.api;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.lilimi.catalog.admin.application.GarmentVariantVersionConflictException;

@RestControllerAdvice(
  assignableTypes = AdminGarmentVariantController.class
)
public class AdminGarmentVariantExceptionHandler {

  @ExceptionHandler({
    GarmentVariantVersionConflictException.class,
    OptimisticLockingFailureException.class
  })
  public ProblemDetail handleVersionConflict(Exception exception) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      "Variant was modified. Reload variants before updating."
    );

    problem.setProperty(
      "code",
      "GARMENT_VARIANT_VERSION_CONFLICT"
    );

    return problem;
  }
}
