package pl.lilimi.payment.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.payment.application.CheckoutSessionResult;
import pl.lilimi.payment.application.OrderPaymentAccessService;
import pl.lilimi.payment.application.StripeCheckoutService;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders/{orderId}/payment-session")
public class OrderPaymentController {

  private final OrderPaymentAccessService access;
  private final ObjectProvider<StripeCheckoutService> checkoutServices;

  public OrderPaymentController(
    OrderPaymentAccessService access,
    ObjectProvider<StripeCheckoutService> checkoutServices
  ) {
    this.access = access;
    this.checkoutServices = checkoutServices;
  }

  @PostMapping
  public CheckoutSessionResult startPayment(
    @PathVariable UUID orderId,
    @RequestHeader("Idempotency-Key") UUID idempotencyKey,
    Authentication authentication,
    HttpServletRequest servletRequest
  ) {
    access.requireAccess(
      orderId,
      authentication,
      servletRequest.getSession(false)
    );

    var checkout = checkoutServices.getIfAvailable();

    if (checkout == null) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENTS_DISABLED"
      );
    }

    return checkout.start(orderId, idempotencyKey);
  }
}
