package pl.lilimi.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {

  @Id
  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "first_name", length = 100)
  private String firstName;

  @Column(name = "last_name", length = 100)
  private String lastName;

  @Column(name = "phone", length = 30)
  private String phone;

  @Column(name = "address_line1", length = 200)
  private String addressLine1;

  @Column(name = "address_line2", length = 200)
  private String addressLine2;

  @Column(name = "postal_code", length = 6)
  private String postalCode;

  @Column(name = "city", length = 100)
  private String city;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected CustomerProfile() {
    // Required by JPA.
  }

  public CustomerProfile(UUID accountId) {
    this.accountId = Objects.requireNonNull(
      accountId, "Account ID is required"
    );
  }

  public void updateContact(
    String firstName,
    String lastName,
    String phone
  ) {
    String normalizedFirstName = normalize(firstName, 100);
    String normalizedLastName = normalize(lastName, 100);
    String normalizedPhone = normalize(phone, 30);

    String fullName = (
      Objects.toString(normalizedFirstName, "") + " " +
        Objects.toString(normalizedLastName, "")
    ).trim();

    if (fullName.length() > 150) {
      throw new IllegalArgumentException("Full name is too long");
    }

    this.firstName = normalizedFirstName;
    this.lastName = normalizedLastName;
    this.phone = normalizedPhone;
  }

  public void updateAddress(
    String addressLine1,
    String addressLine2,
    String postalCode,
    String city,
    String countryCode
  ) {
    String line1 = normalize(addressLine1, 200);
    String line2 = normalize(addressLine2, 200);
    String postal = normalize(postalCode, 6);
    String normalizedCity = normalize(city, 100);
    String country = normalize(countryCode, 2);

    boolean emptyAddress =
      line1 == null &&
        line2 == null &&
        postal == null &&
        normalizedCity == null &&
        country == null;

    if (!emptyAddress && (
      line1 == null ||
        postal == null ||
        !postal.matches("[0-9]{2}-[0-9]{3}") ||
        normalizedCity == null ||
        !"PL".equals(country)
    )) {
      throw new IllegalArgumentException(
        "A complete Polish address is required"
      );
    }

    this.addressLine1 = line1;
    this.addressLine2 = line2;
    this.postalCode = postal;
    this.city = normalizedCity;
    this.countryCode = country;
  }

  private static String normalize(String value, int maxLength) {
    if (value == null) {
      return null;
    }

    String normalized = value.strip();

    if (normalized.isEmpty()) {
      return null;
    }

    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("Value is too long");
    }

    return normalized;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getPhone() {
    return phone;
  }

  public String getAddressLine1() {
    return addressLine1;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public String getCity() {
    return city;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public long getVersion() {
    return version;
  }
}
