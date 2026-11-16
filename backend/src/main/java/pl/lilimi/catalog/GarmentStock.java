package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "garment_stock")
public class GarmentStock {

  @Id
  private UUID id;

  @Column(nullable = false)
  private int quantity;

  protected GarmentStock() {
    // Required by JPA.
  }

  public UUID getId() {
    return id;
  }

  public int getAvailableQuantity() {
    return Math.max(0, quantity);
  }

  public void setAvailableQuantity(int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("Stock quantity cannot be negative");
    }

    this.quantity = quantity;
  }

  public void release(int quantity) {
    this.quantity = Math.addExact(this.quantity, quantity);
  }
}
