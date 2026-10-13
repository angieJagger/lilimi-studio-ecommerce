package pl.lilimi.order.api.customer;

import pl.lilimi.order.domain.OrderContact;

public record CustomerOrderContactResponse(
  String fullName,
  String email,
  String phone
) {

  public static CustomerOrderContactResponse from(OrderContact contact) {
    return new CustomerOrderContactResponse(
      contact.getFullName(),
      contact.getEmail(),
      contact.getPhone()
    );
  }
}
