package pl.lilimi.customer.api;

import pl.lilimi.customer.domain.CustomerProfile;

public record CustomerProfileResponse(
  String email,
  String firstName,
  String lastName,
  String phone,
  String addressLine1,
  String addressLine2,
  String postalCode,
  String city,
  String countryCode,
  Long version
) {

  public static CustomerProfileResponse from(
    String email,
    CustomerProfile profile
  ) {
    return new CustomerProfileResponse(
      email,
      profile == null ? null : profile.getFirstName(),
      profile == null ? null : profile.getLastName(),
      profile == null ? null : profile.getPhone(),
      profile == null ? null : profile.getAddressLine1(),
      profile == null ? null : profile.getAddressLine2(),
      profile == null ? null : profile.getPostalCode(),
      profile == null ? null : profile.getCity(),
      profile == null ? null : profile.getCountryCode(),
      profile == null ? null : profile.getVersion()
    );
  }
}
