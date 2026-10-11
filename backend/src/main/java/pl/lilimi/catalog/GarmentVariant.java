package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "garment_variants")
public class GarmentVariant {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "product_id", length = 64, nullable = false)
  private String productId;

  @Column(name = "pattern_id", length = 64, nullable = false)
  private String patternId;

  @Column(name = "embroidery_option_id", length = 32, nullable = false)
  private String embroideryOptionId;

  @Column(name = "fit", length = 16, nullable = false)
  private String fit;

  @Column(name = "size", length = 8, nullable = false)
  private String size;

  @Column(name = "color", length = 16, nullable = false)
  private String color;

  @Column(name = "price_in_grosz", nullable = false)
  private int priceInGrosz;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected GarmentVariant() {
    // Required by JPA.
  }

  public void changePrice(int priceInGrosz) {
    if (priceInGrosz <= 0) {
      throw new IllegalArgumentException(
        "Price must be greater than zero"
      );
    }

    this.priceInGrosz = priceInGrosz;
  }

  public void changeVisibility(boolean active) {
    this.active = active;
  }

  public UUID getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public String getProductId() {
    return productId;
  }

  public String getPatternId() {
    return patternId;
  }

  public String getEmbroideryOptionId() {
    return embroideryOptionId;
  }

  public String getFit() {
    return fit;
  }

  public String getSize() {
    return size;
  }

  public String getColor() {
    return color;
  }

  public int getPriceInGrosz() {
    return priceInGrosz;
  }

  public boolean isActive() {
    return active;
  }
}
