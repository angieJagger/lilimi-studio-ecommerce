package pl.lilimi.payment.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.service.checkout.SessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

@Component
@ConditionalOnProperty(
  prefix = "lilimi.payments.stripe",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
@Transactional(propagation = Propagation.NEVER)
public class StripeCheckoutGateway {

  private static final Duration CREATION_WINDOW =
    Duration.ofHours(23);

  private final SessionService sessions;
  private final Clock clock;

  public StripeCheckoutGateway(
    SessionService sessions,
    Clock clock
  ) {
    this.sessions = sessions;
    this.clock = clock;
  }

  public Session create(
    PaymentAttempt attempt,
    SessionCreateParams params
  ) throws StripeException {
    Objects.requireNonNull(attempt, "Payment attempt is required");
    Objects.requireNonNull(params, "Checkout parameters are required");

    requireTestAttempt(attempt);

    if (attempt.getStatus() != PaymentAttemptStatus.CREATED) {
      throw new IllegalStateException(
        "Only an uninitialized attempt can create a session"
      );
    }

    if (!clock.instant().isBefore(
      attempt.getCreatedAt().plus(CREATION_WINDOW)
    )) {
      throw new IllegalStateException(
        "PAYMENT_SESSION_RECONCILIATION_REQUIRED"
      );
    }

    if (
      !attempt.getId().toString().equals(
        params.getClientReferenceId()
      )
        || params.getMetadata() == null
        || !attempt.getId().toString().equals(
        params.getMetadata().get("payment_attempt_id")
      )
        || !attempt.getOrderId().toString().equals(
        params.getMetadata().get("order_id")
      )
    ) {
      throw new IllegalArgumentException(
        "Checkout parameters do not match the attempt"
      );
    }

    var options = RequestOptions.builder()
      .setIdempotencyKey(
        "lilimi-checkout-" + attempt.getId()
      )
      .build();

    var session = sessions.create(params, options);

    validateSession(attempt, session);

    return session;
  }

  public Session retrieve(
    PaymentAttempt attempt
  ) throws StripeException {
    Objects.requireNonNull(attempt, "Payment attempt is required");

    requireTestAttempt(attempt);

    String sessionId = attempt.getStripeCheckoutSessionId();

    if (sessionId == null || !sessionId.startsWith("cs_test_")) {
      throw new IllegalArgumentException(
        "A saved sandbox session is required"
      );
    }

    var session = sessions.retrieve(sessionId);

    validateSession(attempt, session);

    return session;
  }

  public Session expire(
    PaymentAttempt attempt
  ) throws StripeException {
    Objects.requireNonNull(
      attempt,
      "Payment attempt is required"
    );

    requireTestAttempt(attempt);

    if (attempt.getStatus() != PaymentAttemptStatus.PENDING) {
      throw new IllegalStateException(
        "Only a pending attempt can expire its session"
      );
    }

    String sessionId = attempt.getStripeCheckoutSessionId();

    if (sessionId == null || !sessionId.startsWith("cs_test_")) {
      throw new IllegalArgumentException(
        "A saved sandbox session is required"
      );
    }

    var options = RequestOptions.builder()
      .setIdempotencyKey(
        "lilimi-expire-checkout-" + attempt.getId()
      )
      .build();

    var session = sessions.expire(sessionId, options);

    validateSession(attempt, session);

    if (
      !"expired".equals(session.getStatus())
        || !"unpaid".equals(session.getPaymentStatus())
    ) {
      throw new IllegalStateException(
        "STRIPE_SESSION_NOT_EXPIRED"
      );
    }

    return session;
  }

  private static void requireTestAttempt(
    PaymentAttempt attempt
  ) {
    if (attempt.isLivemode()) {
      throw new IllegalArgumentException(
        "Live payments are not enabled"
      );
    }
  }

  private static void validateSession(
    PaymentAttempt attempt,
    Session session
  ) {
    if (
      session == null
        || session.getId() == null
        || !session.getId().startsWith("cs_test_")
        || !Boolean.FALSE.equals(session.getLivemode())
        || !"payment".equals(session.getMode())
        || !attempt.getId().toString().equals(
        session.getClientReferenceId()
      )
        || session.getMetadata() == null
        || !attempt.getId().toString().equals(
        session.getMetadata().get("payment_attempt_id")
      )
        || !attempt.getOrderId().toString().equals(
        session.getMetadata().get("order_id")
      )
        || !Long.valueOf(attempt.getAmountInGrosz()).equals(
        session.getAmountTotal()
      )
        || !attempt.getCurrency().equalsIgnoreCase(
        session.getCurrency()
      )
        || session.getExpiresAt() == null
    ) {
      throw new IllegalStateException(
        "STRIPE_SESSION_MISMATCH"
      );
    }

    String savedSessionId =
      attempt.getStripeCheckoutSessionId();

    if (
      savedSessionId != null
        && !savedSessionId.equals(session.getId())
    ) {
      throw new IllegalStateException(
        "STRIPE_SESSION_MISMATCH"
      );
    }
  }
}
