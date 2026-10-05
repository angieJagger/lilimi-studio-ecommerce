package pl.lilimi.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class OrderDelivery {

  @Column(name = "delivery_kind", length = 16, nullable = false)
  private String kind;

  @Column(name = "delivery_method_id", length = 32)
  private String methodId;

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

  protected OrderDelivery() {
    // Required by JPA.
  }

  public static OrderDelivery digital() {
    var delivery = new OrderDelivery();
    delivery.kind = "digital";
    return delivery;
  }

  public static OrderDelivery courier(
    String methodId,
    String addressLine1,
    String addressLine2,
    String postalCode,
    String city,
    String countryCode
  ) {
    var delivery = new OrderDelivery();
    delivery.kind = "courier";
    delivery.methodId = methodId;
    delivery.addressLine1 = addressLine1;
    delivery.addressLine2 = addressLine2;
    delivery.postalCode = postalCode;
    delivery.city = city;
    delivery.countryCode = countryCode;
    return delivery;
  }

  public String getKind() {
    return kind;
  }

  public String getMethodId() {
    return methodId;
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
}
