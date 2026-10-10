package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

  @Id
  @Column(name = "id", length = 64, nullable = false)
  private String id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "translations_revision", nullable = false)
  private long translationsRevision;

  @Column(name = "slug", length = 160, nullable = false, unique = true)
  private String slug;

  @Convert(converter = ProductTypeConverter.class)
  @Column(name = "product_type", length = 32, nullable = false)
  private ProductType productType;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "made_to_order", nullable = false)
  private boolean madeToOrder;

  @Column(name = "personalization_available", nullable = false)
  private boolean personalizationAvailable;

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

  public void changeVisibility(boolean active) {
    this.active = active;
  }

  public void markTranslationsChanged() {
    translationsRevision = Math.addExact(
      translationsRevision,
      1
    );
  }

  public String getId() {
    return id;
  }

  public long getVersion() {
    return version;
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

  public boolean isMadeToOrder() {
    return madeToOrder;
  }

  public boolean isPersonalizationAvailable() {
    return personalizationAvailable;
  }
}
