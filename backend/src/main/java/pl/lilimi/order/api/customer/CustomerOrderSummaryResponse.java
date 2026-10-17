package pl.lilimi.order.api.customer;

import pl.lilimi.order.domain.Order;
import pl.lilimi.payment.application.OrderPaymentSummary;

import java.time.Instant;
import java.util.UUID;

public record CustomerOrderSummaryResponse(
  UUID id,
  Instant createdAt,
  String status,
  String currency,
  long totalInGrosz,
  OrderPaymentSummary payment
) {

  public static CustomerOrderSummaryResponse from(
    Order order,
    OrderPaymentSummary payment
  ) {
    return new CustomerOrderSummaryResponse(
      order.getId(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getCurrency(),
      order.getTotalInGrosz(),
      payment
    );
  }
}
