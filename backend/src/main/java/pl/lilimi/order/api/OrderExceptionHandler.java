package pl.lilimi.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.lilimi.order.application.InvalidOrderDeliveryException;
import pl.lilimi.order.application.OrderProductUnavailableException;
import pl.lilimi.order.application.OrderSubmissionConflictException;

@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {

  @ExceptionHandler(OrderProductUnavailableException.class)
  public ProblemDetail handleUnavailableProduct(
    OrderProductUnavailableException exception
  ) {
    var problem = problem(
      HttpStatus.CONFLICT,
      "Product unavailable",
      "A product or selected variant is no longer available.",
      "ORDER_PRODUCT_UNAVAILABLE"
    );

    problem.setProperty("productId", exception.getProductId());

    return problem;
  }

  @ExceptionHandler(InvalidOrderDeliveryException.class)
  public ProblemDetail handleInvalidDelivery(
    InvalidOrderDeliveryException exception
  ) {
    return problem(
      HttpStatus.BAD_REQUEST,
      "Invalid delivery",
      "The delivery method does not match the order.",
      "ORDER_DELIVERY_INVALID"
    );
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
    MethodArgumentNotValidException exception
  ) {
    var problem = problem(
      HttpStatus.BAD_REQUEST,
      "Invalid order data",
      "One or more order fields are invalid.",
      "ORDER_VALIDATION_FAILED"
    );

    var fields = exception.getBindingResult()
      .getFieldErrors()
      .stream()
      .map(error -> error.getField())
      .distinct()
      .sorted()
      .toList();

    problem.setProperty("fields", fields);

    return problem;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableRequest(
    HttpMessageNotReadableException exception
  ) {
    return problem(
      HttpStatus.BAD_REQUEST,
      "Invalid request body",
      "The request contains malformed JSON or an unsupported value.",
      "ORDER_REQUEST_INVALID"
    );
  }

  @ExceptionHandler(OrderSubmissionConflictException.class)
  public ProblemDetail handleSubmissionConflict(
    OrderSubmissionConflictException exception
  ) {
    return problem(
      HttpStatus.CONFLICT,
      "Order submission conflict",
      "The idempotency key was already used for different order data.",
      "ORDER_SUBMISSION_CONFLICT"
    );
  }
  private ProblemDetail problem(
    HttpStatus status,
    String title,
    String detail,
    String code
  ) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }
}
