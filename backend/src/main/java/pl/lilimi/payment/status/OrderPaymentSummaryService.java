package pl.lilimi.payment.status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderPaymentSummaryService {

  private final PaymentAttemptRepository attempts;
  private final boolean paymentsEnabled;

  public OrderPaymentSummaryService(
    PaymentAttemptRepository attempts,
    @Value("${lilimi.payments.stripe.enabled:false}")
    boolean paymentsEnabled
  ) {
    this.attempts = attempts;
    this.paymentsEnabled = paymentsEnabled;
  }

  public Map<UUID, OrderPaymentSummary> summarize(
    Collection<Order> orders
  ) {
    if (orders.isEmpty()) {
      return Map.of();
    }

    var orderIds = orders.stream()
      .map(Order::getId)
      .distinct()
      .toList();

    var selectedAttempts = new HashMap<UUID, PaymentAttempt>();

    for (var attempt : attempts.findPaymentSummaries(orderIds)) {
      selectedAttempts.put(attempt.getOrderId(), attempt);
    }

    var summaries = new HashMap<UUID, OrderPaymentSummary>();

    for (var order : orders) {
      var attempt = selectedAttempts.get(order.getId());

      String status = statusOf(attempt);

      boolean canPay = paymentsEnabled
        && order.getStatus() == OrderStatus.NEW
        && !"succeeded".equals(status)
        && (attempt == null || !attempt.isLivemode());

      summaries.put(
        order.getId(),
        new OrderPaymentSummary(status, canPay)
      );
    }

    return Map.copyOf(summaries);
  }

  private static String statusOf(PaymentAttempt attempt) {
    if (attempt == null) {
      return "unpaid";
    }

    if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
      return "pending";
    }

    return attempt.getStatus().getValue();
  }
}
