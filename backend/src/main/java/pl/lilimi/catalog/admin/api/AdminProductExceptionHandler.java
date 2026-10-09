package pl.lilimi.catalog.admin.api;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.lilimi.catalog.admin.application.ProductVersionConflictException;

@RestControllerAdvice(
  assignableTypes = AdminProductController.class
)
public class AdminProductExceptionHandler {

  @ExceptionHandler(ProductVersionConflictException.class)
  public ProblemDetail handleVersionConflict(
    ProductVersionConflictException exception
  ) {
    return versionConflict(exception.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleConcurrentUpdate(
    OptimisticLockingFailureException exception
  ) {
    return versionConflict(
      "Product was modified. Reload the product list before updating."
    );
  }

  private ProblemDetail versionConflict(String message) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      message
    );

    problem.setProperty("code", "PRODUCT_VERSION_CONFLICT");

    return problem;
  }
}
