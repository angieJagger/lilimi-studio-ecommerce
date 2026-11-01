package pl.lilimi.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.DigitalDownloadEmailRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class DigitalDownloadEmailProcessor {

  private static final Logger log = LoggerFactory.getLogger(
    DigitalDownloadEmailProcessor.class
  );

  private static final int MAX_ATTEMPTS = 5;

  private static final List<Duration> RETRY_DELAYS = List.of(
    Duration.ofMinutes(1),
    Duration.ofMinutes(5),
    Duration.ofMinutes(15),
    Duration.ofHours(1)
  );

  private final DigitalDownloadEmailRepository queue;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final PaymentAttemptRepository payments;
  private final DigitalDownloadTokenIssuer tokenIssuer;
  private final DigitalDownloadEmailMessageFactory messageFactory;
  private final DigitalDownloadMailService mailService;
  private final Clock clock;

  public DigitalDownloadEmailProcessor(
    DigitalDownloadEmailRepository queue,
    OrderRepository orders,
    OrderItemRepository items,
    PaymentAttemptRepository payments,
    DigitalDownloadTokenIssuer tokenIssuer,
    DigitalDownloadEmailMessageFactory messageFactory,
    DigitalDownloadMailService mailService,
    Clock clock
  ) {
    this.queue = queue;
    this.orders = orders;
    this.items = items;
    this.payments = payments;
    this.tokenIssuer = tokenIssuer;
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

    var order = orders.findByIdForUpdate(entry.orderId())
      .orElseThrow(() -> new IllegalStateException(
        "Order referenced by download email does not exist"
      ));

    if (order.getStatus() == OrderStatus.CANCELLED) {
      queue.markSkipped(entry.orderId(), "ORDER_CANCELLED");
      return true;
    }

    if (!payments.existsByOrderIdAndStatus(
      entry.orderId(),
      PaymentAttemptStatus.SUCCEEDED
    )) {
      queue.markSkipped(entry.orderId(), "ORDER_NOT_PAID");
      return true;
    }

    if (!items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      entry.orderId(),
      "digital"
    )) {
      queue.markSkipped(
        entry.orderId(),
        "DIGITAL_DOWNLOAD_NOT_AVAILABLE"
      );
      return true;
    }

    var issued = tokenIssuer.issue(entry.orderId());

    DigitalDownloadEmailMessage message;

    try {
      message = messageFactory.create(order, issued);
    } catch (IllegalArgumentException | NullPointerException exception) {
      queue.recordFailure(
        entry.orderId(),
        false,
        clock.instant(),
        "MESSAGE_INVALID"
      );

      log.warn(
        "Download email could not be built: orderId={}",
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
        "Download email send failed: orderId={}, attempt={}, retry={}",
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
