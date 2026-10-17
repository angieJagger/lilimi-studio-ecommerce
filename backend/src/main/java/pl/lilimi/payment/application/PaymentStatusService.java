package pl.lilimi.payment.application;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PaymentStatusService {

  private final PaymentAttemptRepository attempts;
  private final OrderPaymentAccessService access;

  public PaymentStatusService(
    PaymentAttemptRepository attempts,
    OrderPaymentAccessService access
  ) {
    this.attempts = attempts;
    this.access = access;
  }

  public PaymentStatusResult getStatus(
    UUID attemptId,
    Authentication authentication,
    HttpSession session
  ) {
    var attempt = attempts.findById(attemptId)
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "PAYMENT_ATTEMPT_NOT_FOUND"
        )
      );

    access.requireAccess(
      attempt.getOrderId(),
      authentication,
      session
    );

    return new PaymentStatusResult(
      attempt.getId(),
      attempt.getStatus().getValue()
    );
  }
}
