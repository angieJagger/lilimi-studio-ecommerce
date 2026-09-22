package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @Column(name = "id", length = 64, nullable = false)
  private String id;

  @Column(name = "slug", length = 160, nullable = false, unique = true)
  private String slug;

  @Convert(converter = ProductTypeConverter.class)
  @Column(name = "product_type", length = 32, nullable = false)
  private ProductType productType;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(
    name = "created_at",
    nullable = false,
    insertable = false,
    updatable = false
  )
  private Instant createdAt;

  protected Product() {
    // Required by JPA.
  }

  public String getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public ProductType getProductType() {
    return productType;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
