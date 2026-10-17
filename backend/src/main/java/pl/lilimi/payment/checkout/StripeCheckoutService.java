package pl.lilimi.payment.checkout;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.stripe.StripeCheckoutGateway;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(
  prefix = "lilimi.payments.stripe",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
@Transactional(propagation = Propagation.NEVER)
public class StripeCheckoutService {

  private final PaymentAttemptService attempts;
  private final CheckoutSessionPersistenceService persistence;
  private final StripeCheckoutGateway gateway;

  public StripeCheckoutService(
    PaymentAttemptService attempts,
    CheckoutSessionPersistenceService persistence,
    StripeCheckoutGateway gateway
  ) {
    this.attempts = attempts;
    this.persistence = persistence;
    this.gateway = gateway;
  }

  public CheckoutSessionResult start(
    UUID orderId,
    UUID idempotencyKey
  ) {
    var attempt = attempts.prepare(orderId, idempotencyKey);
    return openCheckout(attempt);
  }

  public CheckoutSessionResult resume(
    UUID orderId,
    UUID idempotencyKey
  ) {
    var attempt = attempts.prepareForResumption(
      orderId,
      idempotencyKey
    );

    return openCheckout(attempt);
  }

  private CheckoutSessionResult openCheckout(
    PaymentAttempt attempt
  ) {
    var prepared = persistence.load(attempt.getId());
    var current = prepared.attempt();

    if (!isActive(current)) {
      return result(current, null);
    }

    Session session;

    try {
      if (current.getStatus() == PaymentAttemptStatus.CREATED) {
        session = gateway.create(
          current,
          prepared.params()
        );
      } else {
        session = gateway.retrieve(current);
      }
    } catch (StripeException exception) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENT_PROVIDER_UNAVAILABLE"
      );
    } catch (IllegalStateException exception) {
      if ("PAYMENT_SESSION_RECONCILIATION_REQUIRED".equals(
        exception.getMessage()
      )) {
        throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "PAYMENT_SESSION_RECONCILIATION_REQUIRED"
        );
      }

      throw exception;
    }

    boolean expired = "expired".equals(session.getStatus());

    persistence.recordSession(
      current.getOrderId(),
      current.getId(),
      session.getId(),
      Instant.ofEpochSecond(session.getExpiresAt()),
      expired
    );

    // Re-read after saving: a webhook or another request may
    // already have changed the payment or order state.
    var latest = persistence.load(current.getId()).attempt();

    if (
      latest.getStatus() != PaymentAttemptStatus.PENDING
        || !"open".equals(session.getStatus())
        || !"unpaid".equals(session.getPaymentStatus())
    ) {
      return result(latest, null);
    }

    return result(
      latest,
      requireCheckoutUrl(session.getUrl())
    );
  }

  private static boolean isActive(PaymentAttempt attempt) {
    return attempt.getStatus() == PaymentAttemptStatus.CREATED
      || attempt.getStatus() == PaymentAttemptStatus.PENDING;
  }

  private static CheckoutSessionResult result(
    PaymentAttempt attempt,
    String checkoutUrl
  ) {
    return new CheckoutSessionResult(
      attempt.getId(),
      attempt.getStatus().getValue(),
      checkoutUrl
    );
  }

  private static String requireCheckoutUrl(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
        "STRIPE_CHECKOUT_URL_INVALID"
      );
    }

    URI uri;

    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
        "STRIPE_CHECKOUT_URL_INVALID"
      );
    }

    if (
      !"https".equalsIgnoreCase(uri.getScheme())
        || !"checkout.stripe.com".equalsIgnoreCase(
        uri.getHost()
      )
        || uri.getUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
    ) {
      throw new IllegalStateException(
        "STRIPE_CHECKOUT_URL_INVALID"
      );
    }

    return value;
  }
}
