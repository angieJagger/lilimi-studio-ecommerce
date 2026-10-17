package pl.lilimi.payment.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentAttemptService {

  private static final List<PaymentAttemptStatus> ACTIVE_STATUSES =
    List.of(
      PaymentAttemptStatus.CREATED,
      PaymentAttemptStatus.PENDING
    );

  private final OrderRepository orders;
  private final PaymentAttemptRepository payments;
  private final Clock clock;

  public PaymentAttemptService(
    OrderRepository orders,
    PaymentAttemptRepository payments,
    Clock clock
  ) {
    this.orders = orders;
    this.payments = payments;
    this.clock = clock;
  }

  @Transactional
  public PaymentAttempt prepare(
    UUID orderId,
    UUID idempotencyKey
  ) {
    return prepareInternal(orderId, idempotencyKey, false);
  }

  @Transactional
  public PaymentAttempt prepareForResumption(
    UUID orderId,
    UUID idempotencyKey
  ) {
    return prepareInternal(orderId, idempotencyKey, true);
  }

  private PaymentAttempt prepareInternal(
    UUID orderId,
    UUID idempotencyKey,
    boolean resumeActive
  ) {
    Objects.requireNonNull(orderId, "Order ID is required");
    Objects.requireNonNull(
      idempotencyKey,
      "Idempotency key is required"
    );

    var order = orders.findByIdForUpdate(orderId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "ORDER_NOT_FOUND"
      ));

    var existing = payments.findByIdempotencyKey(
      idempotencyKey
    );

    if (existing.isPresent()) {
      var attempt = existing.orElseThrow();

      if (
        !attempt.getOrderId().equals(orderId)
          || attempt.isLivemode()
      ) {
        throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "PAYMENT_IDEMPOTENCY_CONFLICT"
        );
      }

      return attempt;
    }

    if (order.getStatus() != OrderStatus.NEW) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_NOT_PAYABLE"
      );
    }

    if (payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_ALREADY_PAID"
      );
    }

    var active = payments.findByOrderIdAndStatusIn(
      orderId,
      ACTIVE_STATUSES
    );

    if (active.isPresent()) {
      var attempt = active.orElseThrow();

      if (attempt.isLivemode()) {
        throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "LIVE_PAYMENTS_NOT_ENABLED"
        );
      }

      if (resumeActive) {
        return attempt;
      }

      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "PAYMENT_ALREADY_IN_PROGRESS"
      );
    }

    var attempt = new PaymentAttempt(
      orderId,
      idempotencyKey,
      order.getTotalInGrosz(),
      order.getCurrency(),
      false,
      clock.instant()
    );

    return payments.saveAndFlush(attempt);
  }
}
