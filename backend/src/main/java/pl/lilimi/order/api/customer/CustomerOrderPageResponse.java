package pl.lilimi.order.api.customer;

import org.springframework.data.domain.Page;

import java.util.List;

public record CustomerOrderPageResponse(
  List<CustomerOrderSummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static CustomerOrderPageResponse from(
    Page<CustomerOrderSummaryResponse> orders
  ) {
    return new CustomerOrderPageResponse(
      orders.getContent(),
      orders.getNumber(),
      orders.getSize(),
      orders.getTotalElements(),
      orders.getTotalPages()
    );
  }
}
