package pl.lilimi.order.api.admin;

import pl.lilimi.order.domain.OrderContact;

public record AdminOrderContactResponse(
  String fullName,
  String email,
  String phone
) {

  public static AdminOrderContactResponse from(OrderContact contact) {
    return new AdminOrderContactResponse(
      contact.getFullName(),
      contact.getEmail(),
      contact.getPhone()
    );
  }
}
