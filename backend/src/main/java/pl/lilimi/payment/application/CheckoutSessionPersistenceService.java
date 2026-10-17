package pl.lilimi.payment.application;

import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.infrastructure.StripeCheckoutParamsFactory;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class CheckoutSessionPersistenceService {

  private final PaymentAttemptRepository payments;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final StripeCheckoutParamsFactory paramsFactory;

  public CheckoutSessionPersistenceService(
    PaymentAttemptRepository payments,
    OrderRepository orders,
    OrderItemRepository items,
    StripeCheckoutParamsFactory paramsFactory
  ) {
    this.payments = payments;
    this.orders = orders;
    this.items = items;
    this.paramsFactory = paramsFactory;
  }

  @Transactional(readOnly = true)
  public PreparedCheckout load(UUID attemptId) {
    var attempt = payments.findById(attemptId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "PAYMENT_ATTEMPT_NOT_FOUND"
      ));

    var order = orders.findById(attempt.getOrderId())
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "ORDER_NOT_FOUND"
      ));

    boolean active =
      attempt.getStatus() == PaymentAttemptStatus.CREATED
        || attempt.getStatus() == PaymentAttemptStatus.PENDING;

    if (active && order.getStatus() != OrderStatus.NEW) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_NOT_PAYABLE"
      );
    }

    if (
      active
        && payments.existsByOrderIdAndStatus(
        order.getId(),
        PaymentAttemptStatus.SUCCEEDED
      )
    ) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_ALREADY_PAID"
      );
    }

    if (attempt.isLivemode()) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "LIVE_PAYMENTS_NOT_ENABLED"
      );
    }

    SessionCreateParams params = null;

    if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
      var orderItems = items.findByOrder_IdOrderByPositionAsc(
        order.getId()
      );

      params = paramsFactory.create(
        attempt,
        order,
        orderItems
      );
    }

    return new PreparedCheckout(attempt, params);
  }

  @Transactional
  public PaymentAttempt recordSession(
    UUID orderId,
    UUID attemptId,
    String sessionId,
    Instant expiresAt,
    boolean expired
  ) {
    orders.findByIdForUpdate(orderId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "ORDER_NOT_FOUND"
      ));

    var attempt = payments.findByIdForUpdate(attemptId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "PAYMENT_ATTEMPT_NOT_FOUND"
      ));

    if (!orderId.equals(attempt.getOrderId())) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "PAYMENT_ORDER_MISMATCH"
      );
    }

    if (attempt.isLivemode()) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "LIVE_PAYMENTS_NOT_ENABLED"
      );
    }

    if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
      attempt.attachCheckoutSession(sessionId, expiresAt);
    } else if (
      !sessionId.equals(attempt.getStripeCheckoutSessionId())
    ) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "PAYMENT_SESSION_MISMATCH"
      );
    }

    if (expired) {
      attempt.markExpired(sessionId);
    }

    return payments.saveAndFlush(attempt);
  }

  public record PreparedCheckout(
    PaymentAttempt attempt,
    SessionCreateParams params
  ) {
  }
}
