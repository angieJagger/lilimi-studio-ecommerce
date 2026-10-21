package pl.lilimi.order.application.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.order.api.admin.AdminOrderDetailsResponse;
import pl.lilimi.order.api.admin.ChangeOrderStatusRequest;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;
import pl.lilimi.payment.cancellation.CheckoutExpirationQueue;

import java.util.UUID;

@Service
@Validated
public class AdminOrderStatusService {

  private final OrderRepository orders;
  private final AdminOrderQueryService queryService;
  private final PaymentAttemptRepository payments;
  private final CheckoutExpirationQueue expirationQueue;

  public AdminOrderStatusService(
    OrderRepository orders,
    AdminOrderQueryService queryService,
    PaymentAttemptRepository payments,
    CheckoutExpirationQueue expirationQueue
  ) {
    this.orders = orders;
    this.queryService = queryService;
    this.payments = payments;
    this.expirationQueue = expirationQueue;
  }

  @Transactional
  public AdminOrderDetailsResponse changeStatus(
    @NotNull UUID id,
    @NotNull @Valid ChangeOrderStatusRequest request
  ) {
    // Match the lock order used by checkout and webhooks.
    var order = orders.findByIdForUpdate(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Order not found"
      ));

    if (order.getVersion() != request.expectedVersion()) {
      throw new OrderStatusConflictException(
        "ORDER_VERSION_CONFLICT",
        "Order was updated. Reload its details before changing the status"
      );
    }

    var nextStatus = OrderStatus.fromValue(request.status());

    if (
      nextStatus == OrderStatus.CANCELLED
        && order.getStatus() != OrderStatus.CANCELLED
        && payments.existsByOrderIdAndStatus(
        id,
        PaymentAttemptStatus.SUCCEEDED
      )
    ) {
      throw new OrderStatusConflictException(
        "ORDER_PAYMENT_REFUND_REQUIRED",
        "A paid order requires a separate cancellation and refund process"
      );
    }

    try {
      order.changeStatus(nextStatus);
    } catch (IllegalStateException exception) {
      throw new OrderStatusConflictException(
        "ORDER_STATUS_TRANSITION_INVALID",
        "Order status transition is not allowed"
      );
    }

    orders.flush();

    if (order.getStatus() == OrderStatus.CANCELLED) {
      expirationQueue.enqueueForOrder(id);
    }

    return queryService.findOrder(id);
  }
}
