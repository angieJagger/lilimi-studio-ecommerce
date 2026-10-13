package pl.lilimi.order.api.customer;

import pl.lilimi.order.domain.OrderDelivery;

public record CustomerOrderDeliveryResponse(
  String kind,
  String methodId,
  String addressLine1,
  String addressLine2,
  String postalCode,
  String city,
  String countryCode
) {

  public static CustomerOrderDeliveryResponse from(OrderDelivery delivery) {
    return new CustomerOrderDeliveryResponse(
      delivery.getKind(),
      delivery.getMethodId(),
      delivery.getAddressLine1(),
      delivery.getAddressLine2(),
      delivery.getPostalCode(),
      delivery.getCity(),
      delivery.getCountryCode()
    );
  }
}
