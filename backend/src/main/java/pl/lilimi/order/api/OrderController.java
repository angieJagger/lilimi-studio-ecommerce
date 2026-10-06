package pl.lilimi.order.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.order.application.OrderSubmissionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderSubmissionService submissionService;

  public OrderController(OrderSubmissionService submissionService) {
    this.submissionService = submissionService;
  }

  @PostMapping
  public ResponseEntity<CreateOrderResponse> createOrder(
    @RequestHeader("Idempotency-Key") UUID idempotencyKey,
    @Valid @RequestBody CreateOrderRequest request
  ) {
    var order = submissionService.submit(idempotencyKey, request);

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(CreateOrderResponse.from(order));
  }
}
