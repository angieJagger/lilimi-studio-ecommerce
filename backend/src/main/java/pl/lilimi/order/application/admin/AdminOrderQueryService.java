package pl.lilimi.order.application.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.order.api.admin.AdminOrderContactResponse;
import pl.lilimi.order.api.admin.AdminOrderDeliveryResponse;
import pl.lilimi.order.api.admin.AdminOrderDetailsResponse;
import pl.lilimi.order.api.admin.AdminOrderItemResponse;
import pl.lilimi.order.api.admin.AdminOrderSummaryResponse;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.cancellation.CheckoutExpirationQueue;

import java.util.List;
import java.util.UUID;

@Service
public class AdminOrderQueryService {

  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final CheckoutExpirationQueue expirationQueue;

  public AdminOrderQueryService(
    OrderRepository orders,
    OrderItemRepository items,
    CheckoutExpirationQueue expirationQueue
  ) {
    this.orders = orders;
    this.items = items;
    this.expirationQueue = expirationQueue;
  }

  @Transactional(readOnly = true)
  public Page<AdminOrderSummaryResponse> findOrders(
    int page,
    int size
  ) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid pagination");
    }

    var sorting = Sort.by(
      Sort.Order.desc("createdAt"),
      Sort.Order.desc("id")
    );

    var result = orders.findAll(
      PageRequest.of(page, size, sorting)
    );

    var orderIds = result.getContent().stream()
      .map(order -> order.getId())
      .toList();

    var cancellations = expirationQueue.findSummaries(orderIds);

    return result.map(order ->
      AdminOrderSummaryResponse.from(
        order,
        cancellations.get(order.getId())
      )
    );
  }

  @Transactional(readOnly = true)
  public AdminOrderDetailsResponse findOrder(UUID id) {
    var order = orders.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Order not found"
      ));

    var delivery = order.getDelivery();

    var deliveryResponse = new AdminOrderDeliveryResponse(
      delivery.getKind(),
      delivery.getMethodId(),
      delivery.getAddressLine1(),
      delivery.getAddressLine2(),
      delivery.getPostalCode(),
      delivery.getCity(),
      delivery.getCountryCode()
    );

    var itemResponses = items.findByOrder_IdOrderByPositionAsc(id)
      .stream()
      .map(AdminOrderItemResponse::from)
      .toList();

    var cancellation = expirationQueue.findSummaries(
      List.of(order.getId())
    ).get(order.getId());

    return new AdminOrderDetailsResponse(
      order.getId(),
      order.getVersion(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getLanguage(),
      order.getCurrency(),
      AdminOrderContactResponse.from(order.getContact()),
      deliveryResponse,
      itemResponses,
      order.getSubtotalInGrosz(),
      order.getDeliveryPriceInGrosz(),
      order.getTotalInGrosz(),
      cancellation
    );
  }
}
