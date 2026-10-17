package pl.lilimi.payment.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.payment.application.PaymentStatusResult;
import pl.lilimi.payment.application.PaymentStatusService;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments/attempts")
public class PaymentStatusController {

  private final PaymentStatusService payments;

  public PaymentStatusController(
    PaymentStatusService payments
  ) {
    this.payments = payments;
  }

  @GetMapping("/{attemptId}")
  public ResponseEntity<PaymentStatusResult> getStatus(
    @PathVariable UUID attemptId,
    Authentication authentication,
    HttpServletRequest request
  ) {
    var result = payments.getStatus(
      attemptId,
      authentication,
      request.getSession(false)
    );

    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(result);
  }
}
