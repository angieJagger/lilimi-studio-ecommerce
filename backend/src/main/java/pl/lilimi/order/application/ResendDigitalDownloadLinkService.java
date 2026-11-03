package pl.lilimi.order.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.DigitalDownloadEmailRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Service
public class ResendDigitalDownloadLinkService {

  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final PaymentAttemptRepository payments;
  private final DigitalDownloadEmailRepository queue;
  private final Clock clock;
  private final Duration cooldown;

  public ResendDigitalDownloadLinkService(
    OrderRepository orders,
    OrderItemRepository items,
    PaymentAttemptRepository payments,
    DigitalDownloadEmailRepository queue,
    Clock clock,
    @Value("${lilimi.digital-downloads.resend-cooldown:PT5M}")
    Duration cooldown
  ) {
    this.orders = orders;
    this.items = items;
    this.payments = payments;
    this.queue = queue;
    this.clock = clock;
    this.cooldown = Objects.requireNonNull(cooldown);

    if (cooldown.isZero() || cooldown.isNegative()) {
      throw new IllegalArgumentException(
        "Download email cooldown must be positive"
      );
    }
  }

  @Transactional
  public void request(UUID orderId, String email) {
    if (orderId == null || email == null || email.isBlank()) {
      return;
    }

    var found = orders.findById(orderId);

    if (found.isEmpty()) {
      return;
    }

    var order = found.orElseThrow();

    if (!order.getContact().getEmail().strip()
      .equalsIgnoreCase(email.strip())) {
      return;
    }

    if (order.getStatus() == OrderStatus.CANCELLED) {
      return;
    }

    if (!payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )) {
      return;
    }

    if (!items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      orderId,
      "digital"
    )) {
      return;
    }

    queue.enqueueAgain(orderId, clock.instant(), cooldown);
  }
}
