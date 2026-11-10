package pl.lilimi.catalog.admin.api;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pl.lilimi.catalog.admin.application.ProductVersionConflictException;
import pl.lilimi.catalog.admin.application.ProductSlugConflictException;
import pl.lilimi.catalog.admin.application.DigitalProductNotReadyException;
import pl.lilimi.catalog.admin.application.PhysicalProductNotPublishableException;
import pl.lilimi.catalog.admin.application.ProductCodeConflictException;
import pl.lilimi.catalog.admin.application.SweatshirtNotReadyException;
import pl.lilimi.catalog.digital.InvalidDigitalProductPackageException;

@RestControllerAdvice(
  assignableTypes = {
    AdminProductController.class,
    AdminProductImageController.class
  }
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

  @ExceptionHandler(ProductSlugConflictException.class)
  public ProblemDetail handleSlugConflict(
    ProductSlugConflictException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      exception.getMessage()
    );
    problem.setProperty("code", "PRODUCT_SLUG_CONFLICT");
    return problem;
  }

  @ExceptionHandler(DigitalProductNotReadyException.class)
  public ProblemDetail handleDigitalProductNotReady(
    DigitalProductNotReadyException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      exception.getMessage()
    );
    problem.setProperty("code", "DIGITAL_PRODUCT_NOT_READY");
    return problem;
  }

  @ExceptionHandler(ProductCodeConflictException.class)
  public ProblemDetail handleProductCodeConflict(ProductCodeConflictException exception) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      exception.getMessage()
    );
    problem.setProperty("code", "PRODUCT_CODE_CONFLICT");
    return problem;
  }

  @ExceptionHandler(SweatshirtNotReadyException.class)
  public ProblemDetail handleSweatshirtNotReady(SweatshirtNotReadyException exception) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      exception.getMessage()
    );
    problem.setProperty("code", "SWEATSHIRT_NOT_READY");
    return problem;
  }

  @ExceptionHandler(PhysicalProductNotPublishableException.class)
  public ProblemDetail handlePhysicalProductNotPublishable(
    PhysicalProductNotPublishableException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setProperty("code", "PHYSICAL_PRODUCT_NOT_PUBLISHABLE");
    return problem;
  }

  @ExceptionHandler(InvalidDigitalProductPackageException.class)
  public ProblemDetail handleInvalidPackage() {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The uploaded ZIP package is invalid.");
    problem.setProperty("code", "DIGITAL_PACKAGE_INVALID");
    return problem;
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
