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

import java.util.UUID;

@Service
@Validated
public class AdminOrderStatusService {

  private final OrderRepository orders;
  private final AdminOrderQueryService queryService;

  public AdminOrderStatusService(
    OrderRepository orders,
    AdminOrderQueryService queryService
  ) {
    this.orders = orders;
    this.queryService = queryService;
  }

  @Transactional
  public AdminOrderDetailsResponse changeStatus(
    @NotNull UUID id,
    @NotNull @Valid ChangeOrderStatusRequest request
  ) {
    var order = orders.findById(id)
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

    try {
      order.changeStatus(nextStatus);
    } catch (IllegalStateException exception) {
      throw new OrderStatusConflictException(
        "ORDER_STATUS_TRANSITION_INVALID",
        "Order status transition is not allowed"
      );
    }

    orders.flush();

    return queryService.findOrder(id);
  }
}
