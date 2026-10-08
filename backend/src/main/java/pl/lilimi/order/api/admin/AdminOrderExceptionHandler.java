package pl.lilimi.order.api.admin;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.lilimi.order.application.admin.OrderStatusConflictException;

@RestControllerAdvice(
  assignableTypes = AdminOrderController.class
)
public class AdminOrderExceptionHandler {

  @ExceptionHandler(OrderStatusConflictException.class)
  public ProblemDetail handleStatusConflict(
    OrderStatusConflictException exception
  ) {
    return conflict(
      exception.getCode(),
      exception.getMessage()
    );
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleConcurrentUpdate(
    OptimisticLockingFailureException exception
  ) {
    return conflict(
      "ORDER_VERSION_CONFLICT",
      "Order was updated. Reload its details before changing the status"
    );
  }

  private ProblemDetail conflict(String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      detail
    );

    problem.setTitle("Order update conflict");
    problem.setProperty("code", code);

    return problem;
  }
}
