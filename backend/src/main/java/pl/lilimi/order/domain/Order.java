package pl.lilimi.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Convert(converter = OrderStatusConverter.class)
  @Column(name = "status", length = 32, nullable = false)
  private OrderStatus status;

  @Column(name = "language", length = 2, nullable = false)
  private String language;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Embedded
  private OrderContact contact;

  @Embedded
  private OrderDelivery delivery;

  @Column(name = "subtotal_in_grosz", nullable = false)
  private long subtotalInGrosz;

  @Column(name = "delivery_price_in_grosz", nullable = false)
  private long deliveryPriceInGrosz;

  @Column(name = "total_in_grosz", nullable = false)
  private long totalInGrosz;

  protected Order() {
    // Required by JPA.
  }

  public Order(
    String language,
    OrderContact contact,
    OrderDelivery delivery,
    long subtotalInGrosz,
    long deliveryPriceInGrosz
  ) {
    if (subtotalInGrosz <= 0 || deliveryPriceInGrosz < 0) {
      throw new IllegalArgumentException("Invalid order amounts");
    }

    this.status = OrderStatus.NEW;
    this.language = language;
    this.currency = "PLN";
    this.contact = contact;
    this.delivery = delivery;
    this.subtotalInGrosz = subtotalInGrosz;
    this.deliveryPriceInGrosz = deliveryPriceInGrosz;
    this.totalInGrosz = Math.addExact(
      subtotalInGrosz,
      deliveryPriceInGrosz
    );
  }

  @PrePersist
  private void initializeCreatedAt() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public String getLanguage() {
    return language;
  }

  public String getCurrency() {
    return currency;
  }

  public OrderContact getContact() {
    return contact;
  }

  public OrderDelivery getDelivery() {
    return delivery;
  }

  public long getSubtotalInGrosz() {
    return subtotalInGrosz;
  }

  public long getDeliveryPriceInGrosz() {
    return deliveryPriceInGrosz;
  }

  public long getTotalInGrosz() {
    return totalInGrosz;
  }
}
