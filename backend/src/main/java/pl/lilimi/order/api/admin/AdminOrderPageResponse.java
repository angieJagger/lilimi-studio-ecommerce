package pl.lilimi.order.api.admin;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminOrderPageResponse(
  List<AdminOrderSummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static AdminOrderPageResponse from(
    Page<AdminOrderSummaryResponse> orders
  ) {
    return new AdminOrderPageResponse(
      orders.getContent(),
      orders.getNumber(),
      orders.getSize(),
      orders.getTotalElements(),
      orders.getTotalPages()
    );
  }
}
