package pl.lilimi.order.api.admin;

public record AdminOrderDeliveryResponse(
  String kind,
  String methodId,
  String addressLine1,
  String addressLine2,
  String postalCode,
  String city,
  String countryCode
) {
}
