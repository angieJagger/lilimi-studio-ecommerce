package pl.lilimi.order.application;

import pl.lilimi.order.domain.OrderDelivery;

public record OrderDeliveryQuote(
  OrderDelivery delivery,
  long priceInGrosz
) {
}
