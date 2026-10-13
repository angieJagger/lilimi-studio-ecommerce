package pl.lilimi.order.api.customer;

import org.springframework.data.domain.Page;
import pl.lilimi.order.domain.Order;

import java.util.List;

public record CustomerOrderPageResponse(
  List<CustomerOrderSummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static CustomerOrderPageResponse from(Page<Order> orders) {
    return new CustomerOrderPageResponse(
      orders.getContent().stream()
        .map(CustomerOrderSummaryResponse::from)
        .toList(),
      orders.getNumber(),
      orders.getSize(),
      orders.getTotalElements(),
      orders.getTotalPages()
    );
  }
}
