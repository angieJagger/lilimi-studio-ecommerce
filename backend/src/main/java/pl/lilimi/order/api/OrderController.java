package pl.lilimi.order.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import pl.lilimi.auth.application.CurrentAccountService;
import pl.lilimi.order.application.OrderSubmissionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderSubmissionService submissionService;
  private final CurrentAccountService currentAccountService;

  public OrderController(
    OrderSubmissionService submissionService,
    CurrentAccountService currentAccountService
  ) {
    this.submissionService = submissionService;
    this.currentAccountService = currentAccountService;
  }

  @PostMapping
  public ResponseEntity<CreateOrderResponse> createOrder(
    @RequestHeader("Idempotency-Key") UUID idempotencyKey,
    @Valid @RequestBody CreateOrderRequest request,
    Authentication authentication
  ) {
    var accountId = currentAccountService.findAccountId(authentication);

    var order = submissionService.submit(
      idempotencyKey,
      request,
      accountId
    );

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(CreateOrderResponse.from(order));
  }
}
