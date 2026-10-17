package pl.lilimi.order.api.customer;

import pl.lilimi.payment.status.OrderPaymentSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerOrderDetailsResponse(
  UUID id,
  Instant createdAt,
  String status,
  String language,
  String currency,
  CustomerOrderContactResponse contact,
  CustomerOrderDeliveryResponse delivery,
  List<CustomerOrderItemResponse> items,
  long subtotalInGrosz,
  long deliveryPriceInGrosz,
  long totalInGrosz,
  OrderPaymentSummary payment
) {
}
