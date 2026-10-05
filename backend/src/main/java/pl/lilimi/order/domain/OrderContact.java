package pl.lilimi.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class OrderContact {

  @Column(name = "customer_full_name", length = 150, nullable = false)
  private String fullName;

  @Column(name = "customer_email", length = 254, nullable = false)
  private String email;

  @Column(name = "customer_phone", length = 30)
  private String phone;

  protected OrderContact() {
    // Required by JPA.
  }

  public OrderContact(String fullName, String email, String phone) {
    this.fullName = fullName;
    this.email = email;
    this.phone = phone;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }
}
