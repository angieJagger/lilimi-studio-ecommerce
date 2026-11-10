package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

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

  @Column(name = "price_revision", nullable = false)
  private long priceRevision;

  @Column(name = "images_revision", nullable = false)
  private long imagesRevision;

  @Column(name = "slug", length = 160, nullable = false, unique = true)
  private String slug;

  @Column(name = "product_code", length = 64)
  private String productCode;

  @Convert(converter = ProductTypeConverter.class)
  @Column(name = "product_type", length = 32, nullable = false)
  private ProductType productType;

  @Convert(converter = PhysicalDraftConfigurationConverter.class)
  @Column(name = "physical_configuration", columnDefinition = "text")
  private PhysicalDraftConfiguration physicalConfiguration;

  @Column(name = "stock_quantity")
  private Integer stockQuantity;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "archived", nullable = false)
  private boolean archived;

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

  public static Product createDraft(
    String slug,
    String productCode,
    ProductType productType
  ) {
    var product = new Product();
    product.id = UUID.randomUUID().toString();
    product.slug = slug;
    product.productCode = productCode;
    product.productType = productType;
    product.active = false;
    return product;
  }

  public void changePhysicalConfiguration(PhysicalDraftConfiguration configuration) {
    if (productType != ProductType.PHYSICAL) {
      throw new IllegalStateException("Only physical products have a physical configuration");
    }
    physicalConfiguration = configuration;
  }

  public PhysicalDraftConfiguration getPhysicalConfiguration() {
    return physicalConfiguration;
  }

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void changeStockQuantity(int quantity) {
    if (productType != ProductType.PHYSICAL || quantity < 0) {
      throw new IllegalArgumentException("Invalid physical product stock quantity");
    }

    stockQuantity = quantity;
  }

  public boolean isArchived() {
    return archived;
  }

  public void archive() {
    active = false;
    archived = true;
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

  public void markPriceChanged() {
    priceRevision = Math.addExact(priceRevision, 1);
  }

  public void markImagesChanged() {
    imagesRevision = Math.addExact(imagesRevision, 1);
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

  public String getProductCode() {
    return productCode;
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
