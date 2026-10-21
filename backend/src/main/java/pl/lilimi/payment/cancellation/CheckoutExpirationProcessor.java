package pl.lilimi.payment.cancellation;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.payment.checkout.CheckoutSessionPersistenceService;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.stripe.StripeCheckoutGateway;

import java.time.Clock;
import java.time.Instant;

@Service
@ConditionalOnProperty(
  prefix = "lilimi.payments.stripe",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
@Transactional(propagation = Propagation.NEVER)
public class CheckoutExpirationProcessor {

  private final CheckoutExpirationQueue queue;
  private final CheckoutSessionPersistenceService persistence;
  private final StripeCheckoutGateway gateway;
  private final Clock clock;

  public CheckoutExpirationProcessor(
    CheckoutExpirationQueue queue,
    CheckoutSessionPersistenceService persistence,
    StripeCheckoutGateway gateway,
    Clock clock
  ) {
    this.queue = queue;
    this.persistence = persistence;
    this.gateway = gateway;
    this.clock = clock;
  }

  public boolean processNext() {
    var next = queue.claimNext(clock.instant());

    if (next.isEmpty()) {
      return false;
    }

    var claim = next.orElseThrow();

    try {
      process(claim);
    } catch (StripeException exception) {
      queue.retry(
        claim,
        clock.instant(),
        "STRIPE_UNAVAILABLE"
      );
    } catch (ResponseStatusException exception) {
      review(claim, "EXPIRATION_DATA_INVALID");
    } catch (IllegalArgumentException exception) {
      review(claim, "EXPIRATION_DATA_INVALID");
    } catch (IllegalStateException exception) {
      String code =
        "PAYMENT_SESSION_RECONCILIATION_REQUIRED".equals(
          exception.getMessage()
        )
          ? "PAYMENT_SESSION_RECONCILIATION_REQUIRED"
          : "EXPIRATION_DATA_INVALID";

      review(claim, code);
    }

    return true;
  }

  private void process(
    CheckoutExpirationQueue.Claim claim
  ) throws StripeException {
    var prepared = persistence.loadForCancellation(
      claim.paymentAttemptId()
    );

    var attempt = prepared.attempt();

    if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
      review(claim, "PAYMENT_ALREADY_COMPLETED");
      return;
    }

    if (
      attempt.getStatus() == PaymentAttemptStatus.EXPIRED
        || (
        attempt.getStatus() == PaymentAttemptStatus.FAILED
          && attempt.getStripeCheckoutSessionId() == null
      )
    ) {
      complete(claim);
      return;
    }

    Session session =
      attempt.getStatus() == PaymentAttemptStatus.CREATED
        ? gateway.create(attempt, prepared.params())
        : gateway.retrieve(attempt);

    var current = recordSession(attempt, session);

    if (current.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
      review(claim, "PAYMENT_ALREADY_COMPLETED");
      return;
    }

    if (
      "expired".equals(session.getStatus())
        || current.getStatus() == PaymentAttemptStatus.EXPIRED
    ) {
      complete(claim);
      return;
    }

    if ("complete".equals(session.getStatus())) {
      review(
        claim,
        "paid".equals(session.getPaymentStatus())
          ? "PAYMENT_ALREADY_COMPLETED"
          : "PAYMENT_PROCESSING"
      );
      return;
    }

    if (
      !"open".equals(session.getStatus())
        || !"unpaid".equals(session.getPaymentStatus())
    ) {
      review(claim, "STRIPE_SESSION_STATE_INVALID");
      return;
    }

    if (current.getStatus() != PaymentAttemptStatus.PENDING) {
      review(claim, "PAYMENT_STATE_CHANGED");
      return;
    }

    var expired = gateway.expire(current);
    var saved = recordSession(current, expired);

    if (saved.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
      review(claim, "PAYMENT_ALREADY_COMPLETED");
      return;
    }

    complete(claim);
  }

  private PaymentAttempt recordSession(
    PaymentAttempt attempt,
    Session session
  ) {
    return persistence.recordSession(
      attempt.getOrderId(),
      attempt.getId(),
      session.getId(),
      Instant.ofEpochSecond(session.getExpiresAt()),
      "expired".equals(session.getStatus())
    );
  }

  private void complete(
    CheckoutExpirationQueue.Claim claim
  ) {
    queue.complete(claim, clock.instant());
  }

  private void review(
    CheckoutExpirationQueue.Claim claim,
    String code
  ) {
    queue.needsReview(
      claim,
      clock.instant(),
      code
    );
  }
}
