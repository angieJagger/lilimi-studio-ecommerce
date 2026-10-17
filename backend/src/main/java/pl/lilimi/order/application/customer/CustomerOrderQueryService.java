package pl.lilimi.order.application.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.order.api.customer.CustomerOrderContactResponse;
import pl.lilimi.order.api.customer.CustomerOrderDeliveryResponse;
import pl.lilimi.order.api.customer.CustomerOrderDetailsResponse;
import pl.lilimi.order.api.customer.CustomerOrderItemResponse;
import pl.lilimi.order.api.customer.CustomerOrderSummaryResponse;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.application.OrderPaymentSummaryService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerOrderQueryService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final OrderPaymentSummaryService paymentSummaries;

  public CustomerOrderQueryService(
    OrderRepository orderRepository,
    OrderItemRepository orderItemRepository,
    OrderPaymentSummaryService paymentSummaries
  ) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.paymentSummaries = paymentSummaries;
  }

  public Page<CustomerOrderSummaryResponse> findOrders(
    UUID customerAccountId,
    int page,
    int size
  ) {
    Objects.requireNonNull(
      customerAccountId,
      "Customer account ID is required"
    );

    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid pagination");
    }

    var pageable = PageRequest.of(
      page,
      size,
      Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id")
      )
    );

    var orders = orderRepository.findByCustomerAccountId(
      customerAccountId,
      pageable
    );

    var payments = paymentSummaries.summarize(
      orders.getContent()
    );

    return orders.map(order ->
      CustomerOrderSummaryResponse.from(
        order,
        payments.get(order.getId())
      )
    );
  }

  public CustomerOrderDetailsResponse findOrder(
    UUID customerAccountId,
    UUID orderId
  ) {
    Objects.requireNonNull(
      customerAccountId,
      "Customer account ID is required"
    );
    Objects.requireNonNull(orderId, "Order ID is required");

    var order = orderRepository.findByIdAndCustomerAccountId(
        orderId,
        customerAccountId
      )
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Order not found"
      ));

    var items = orderItemRepository
      .findByOrder_IdOrderByPositionAsc(order.getId())
      .stream()
      .map(CustomerOrderItemResponse::from)
      .toList();

    var payment = paymentSummaries.summarize(
      List.of(order)
    ).get(order.getId());

    return new CustomerOrderDetailsResponse(
      order.getId(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getLanguage(),
      order.getCurrency(),
      CustomerOrderContactResponse.from(order.getContact()),
      CustomerOrderDeliveryResponse.from(order.getDelivery()),
      items,
      order.getSubtotalInGrosz(),
      order.getDeliveryPriceInGrosz(),
      order.getTotalInGrosz(),
      payment
    );
  }
}
