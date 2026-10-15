package pl.lilimi.customer.api;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCustomerProfileRequest(
  @Size(max = 100) String firstName,
  @Size(max = 100) String lastName,
  @Size(max = 30) String phone,
  @Size(max = 200) String addressLine1,
  @Size(max = 200) String addressLine2,
  @Size(max = 6) String postalCode,
  @Size(max = 100) String city,
  @Size(max = 2) String countryCode,
  @PositiveOrZero Long version
) {
}
