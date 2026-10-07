package pl.lilimi.order.api.admin;

import org.springframework.data.domain.Page;
import pl.lilimi.order.domain.Order;

import java.util.List;

public record AdminOrderPageResponse(
  List<AdminOrderSummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static AdminOrderPageResponse from(Page<Order> orders) {
    return new AdminOrderPageResponse(
      orders.getContent().stream()
        .map(AdminOrderSummaryResponse::from)
        .toList(),
      orders.getNumber(),
      orders.getSize(),
      orders.getTotalElements(),
      orders.getTotalPages()
    );
  }
}
