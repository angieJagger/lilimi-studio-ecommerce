package pl.lilimi.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.order.persistence.OrderConfirmationEmailRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class OrderConfirmationProcessor {

  private static final Logger log = LoggerFactory.getLogger(
    OrderConfirmationProcessor.class
  );

  private static final int MAX_ATTEMPTS = 5;

  private static final List<Duration> RETRY_DELAYS = List.of(
    Duration.ofMinutes(1),
    Duration.ofMinutes(5),
    Duration.ofMinutes(15),
    Duration.ofHours(1)
  );

  private final OrderConfirmationEmailRepository queue;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final OrderConfirmationMessageFactory messageFactory;
  private final OrderConfirmationMailService mailService;
  private final Clock clock;

  public OrderConfirmationProcessor(
    OrderConfirmationEmailRepository queue,
    OrderRepository orders,
    OrderItemRepository items,
    OrderConfirmationMessageFactory messageFactory,
    OrderConfirmationMailService mailService,
    Clock clock
  ) {
    this.queue = queue;
    this.orders = orders;
    this.items = items;
    this.messageFactory = messageFactory;
    this.mailService = mailService;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean processNext() {
    var pending = queue.lockNext(clock.instant());

    if (pending.isEmpty()) {
      return false;
    }

    var entry = pending.orElseThrow();

    var order = orders.findById(entry.orderId())
      .orElseThrow(() -> new IllegalStateException(
        "Order referenced by confirmation does not exist"
      ));

    var orderItems = items.findByOrder_IdOrderByPositionAsc(
      entry.orderId()
    );

    OrderConfirmationMessage message;

    try {
      message = messageFactory.create(order, orderItems);
    } catch (IllegalArgumentException | NullPointerException exception) {
      queue.recordFailure(
        entry.orderId(),
        false,
        clock.instant(),
        "MESSAGE_INVALID"
      );

      log.warn(
        "Order confirmation could not be built: orderId={}",
        entry.orderId()
      );

      return true;
    }

    try {
      mailService.send(message);
    } catch (RuntimeException exception) {
      int attemptsAfterFailure = entry.attempts() + 1;
      boolean retry = attemptsAfterFailure < MAX_ATTEMPTS;

      var nextAttemptAt = retry
        ? clock.instant().plus(
        RETRY_DELAYS.get(attemptsAfterFailure - 1)
      )
        : clock.instant();

      queue.recordFailure(
        entry.orderId(),
        retry,
        nextAttemptAt,
        "SMTP_SEND_FAILED"
      );

      log.warn(
        "Order confirmation send failed: orderId={}, attempt={}, retry={}",
        entry.orderId(),
        attemptsAfterFailure,
        retry
      );

      return true;
    }

    queue.markSent(entry.orderId(), clock.instant());

    return true;
  }
}
