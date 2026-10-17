package pl.lilimi.payment.application;

import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;
import pl.lilimi.payment.persistence.StripeWebhookEventRepository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class StripeWebhookProcessor {

  private static final Set<String> SUPPORTED_EVENTS = Set.of(
    "checkout.session.completed",
    "checkout.session.async_payment_succeeded",
    "checkout.session.async_payment_failed",
    "checkout.session.expired"
  );

  private final OrderRepository orders;
  private final PaymentAttemptRepository attempts;
  private final StripeWebhookEventRepository events;

  public StripeWebhookProcessor(
    OrderRepository orders,
    PaymentAttemptRepository attempts,
    StripeWebhookEventRepository events
  ) {
    this.orders = orders;
    this.attempts = attempts;
    this.events = events;
  }

  @Transactional
  public void process(Event event) {
    if (
      event == null
        || !Boolean.FALSE.equals(event.getLivemode())
        || event.getId() == null
        || event.getType() == null
    ) {
      throw invalidEvent();
    }

    if (!SUPPORTED_EVENTS.contains(event.getType())) {
      return;
    }

    var object = event.getDataObjectDeserializer()
      .getObject()
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "STRIPE_WEBHOOK_API_VERSION_MISMATCH"
        )
      );

    if (!(object instanceof Session session)) {
      throw invalidEvent();
    }

    if (session.getMetadata() == null) {
      throw invalidEvent();
    }

    UUID orderId = parseId(
      session.getMetadata().get("order_id")
    );

    UUID attemptId = parseId(
      session.getMetadata().get("payment_attempt_id")
    );

    // Keep the same lock order as checkout creation:
    // order first, payment attempt second.
    orders.findByIdForUpdate(orderId)
      .orElseThrow(StripeWebhookProcessor::invalidEvent);

    var attempt = attempts.findByIdForUpdate(attemptId)
      .orElseThrow(StripeWebhookProcessor::invalidEvent);

    validateSession(event, session, attempt, orderId);

    if (!events.tryRecord(
      event.getId(),
      event.getType(),
      false
    )) {
      return;
    }

    // A webhook may arrive before the checkout request
    // has saved the session returned by Stripe.
    if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
      attempt.attachCheckoutSession(
        session.getId(),
        Instant.ofEpochSecond(session.getExpiresAt())
      );
    }

    switch (event.getType()) {
      case "checkout.session.completed" -> {
        if ("paid".equals(session.getPaymentStatus())) {
          confirmPayment(event, session, attempt);
        }
        // An unpaid completed session may still be waiting
        // for an asynchronous payment method.
      }

      case "checkout.session.async_payment_succeeded" ->
        confirmPayment(event, session, attempt);

      case "checkout.session.async_payment_failed" ->
        attempt.markFailed(session.getId());

      case "checkout.session.expired" ->
        attempt.markExpired(session.getId());

      default -> throw invalidEvent();
    }

    attempts.saveAndFlush(attempt);
  }

  private static void validateSession(
    Event event,
    Session session,
    PaymentAttempt attempt,
    UUID orderId
  ) {
    if (
      attempt.isLivemode()
        || !attempt.getOrderId().equals(orderId)
        || !attempt.getId().toString().equals(
        session.getClientReferenceId()
      )
        || !Boolean.FALSE.equals(session.getLivemode())
        || !"payment".equals(session.getMode())
        || session.getId() == null
        || !session.getId().startsWith("cs_test_")
        || session.getId().length() > 255
        || session.getAmountTotal() == null
        || session.getAmountTotal() != attempt.getAmountInGrosz()
        || !attempt.getCurrency().equalsIgnoreCase(
        session.getCurrency()
      )
        || session.getExpiresAt() == null
        || !Instant.ofEpochSecond(session.getExpiresAt())
        .isAfter(attempt.getCreatedAt())
        || event.getCreated() == null
        || event.getCreated() <= 0
    ) {
      throw invalidEvent();
    }

    String savedSessionId =
      attempt.getStripeCheckoutSessionId();

    if (
      savedSessionId != null
        && !savedSessionId.equals(session.getId())
    ) {
      throw invalidEvent();
    }

    switch (event.getType()) {
      case "checkout.session.completed",
        "checkout.session.async_payment_succeeded",
        "checkout.session.async_payment_failed" -> {
        if (!"complete".equals(session.getStatus())) {
          throw invalidEvent();
        }
      }

      case "checkout.session.expired" -> {
        if (!"expired".equals(session.getStatus())) {
          throw invalidEvent();
        }
      }

      default -> throw invalidEvent();
    }

    if (
      "checkout.session.completed".equals(event.getType())
        && !"paid".equals(session.getPaymentStatus())
        && !"unpaid".equals(session.getPaymentStatus())
    ) {
      throw invalidEvent();
    }

    if (
      "checkout.session.async_payment_failed".equals(
        event.getType()
      )
        && !"unpaid".equals(session.getPaymentStatus())
    ) {
      throw invalidEvent();
    }
  }

  private static void confirmPayment(
    Event event,
    Session session,
    PaymentAttempt attempt
  ) {
    if (
      !"paid".equals(session.getPaymentStatus())
        || session.getPaymentIntent() == null
        || !session.getPaymentIntent().startsWith("pi_")
    ) {
      throw invalidEvent();
    }

    attempt.confirmPayment(
      session.getId(),
      session.getPaymentIntent(),
      session.getAmountTotal(),
      session.getCurrency(),
      false,
      Instant.ofEpochSecond(event.getCreated())
    );
  }

  private static UUID parseId(String value) {
    if (value == null) {
      throw invalidEvent();
    }

    try {
      UUID id = UUID.fromString(value);

      if (!id.toString().equals(value)) {
        throw invalidEvent();
      }

      return id;
    } catch (IllegalArgumentException exception) {
      throw invalidEvent();
    }
  }

  private static ResponseStatusException invalidEvent() {
    return new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "STRIPE_WEBHOOK_DATA_INVALID"
    );
  }
}
