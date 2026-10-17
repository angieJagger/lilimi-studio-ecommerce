package pl.lilimi.order.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import pl.lilimi.auth.application.CurrentAccountService;
import pl.lilimi.order.application.OrderSubmissionService;
import pl.lilimi.payment.application.GuestOrderAccessService;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderSubmissionService submissionService;
  private final CurrentAccountService currentAccountService;
  private final GuestOrderAccessService guestAccess;
  private final boolean paymentsEnabled;

  public OrderController(
    OrderSubmissionService submissionService,
    CurrentAccountService currentAccountService,
    GuestOrderAccessService guestAccess,
    @Value("${lilimi.payments.stripe.enabled:false}")
    boolean paymentsEnabled
  ) {
    this.submissionService = submissionService;
    this.currentAccountService = currentAccountService;
    this.guestAccess = guestAccess;
    this.paymentsEnabled = paymentsEnabled;
  }

  @PostMapping
  public ResponseEntity<CreateOrderResponse> createOrder(
    @RequestHeader("Idempotency-Key") UUID idempotencyKey,
    @Valid @RequestBody CreateOrderRequest request,
    Authentication authentication,
    HttpServletRequest servletRequest
  ) {
    var accountId = currentAccountService.findAccountId(
      authentication
    );

    var order = submissionService.submit(
      idempotencyKey,
      request,
      accountId
    );

    if (order.getCustomerAccountId() == null) {
      guestAccess.grant(
        servletRequest.getSession(true),
        order.getId()
      );
    }

    return ResponseEntity
      .status(HttpStatus.CREATED)
      .body(CreateOrderResponse.from(order, paymentsEnabled));
  }
}
