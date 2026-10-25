package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images")
public class ProductImage {

  @Id
  private UUID id;

  @Column(name = "product_id", nullable = false, length = 64)
  private String productId;

  @Column(name = "storage_key", nullable = false, unique = true, length = 160)
  private String storageKey;

  @Column(name = "content_type", nullable = false, length = 32)
  private String contentType;

  @Column(name = "byte_size", nullable = false)
  private long byteSize;

  @Column(nullable = false)
  private int width;

  @Column(nullable = false)
  private int height;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "primary_image", nullable = false)
  private boolean primaryImage;

  @Column(name = "alt_pl", nullable = false, length = 250)
  private String altPl;

  @Column(name = "alt_en", nullable = false, length = 250)
  private String altEn;

  @Column(
    name = "created_at",
    nullable = false,
    insertable = false,
    updatable = false
  )
  private Instant createdAt;

  protected ProductImage() {
    // Required by JPA.
  }

  public ProductImage(
    UUID id,
    String productId,
    String storageKey,
    String contentType,
    long byteSize,
    int width,
    int height,
    int sortOrder,
    boolean primaryImage,
    String altPl,
    String altEn
  ) {
    this.id = id;
    this.productId = productId;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.byteSize = byteSize;
    this.width = width;
    this.height = height;
    this.sortOrder = sortOrder;
    this.primaryImage = primaryImage;
    this.altPl = altPl;
    this.altEn = altEn;
  }

  public void updateAlternativeText(String altPl, String altEn) {
    this.altPl = altPl;
    this.altEn = altEn;
  }

  public void replaceFile(
    String storageKey,
    String contentType,
    long byteSize,
    int width,
    int height
  ) {
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.byteSize = byteSize;
    this.width = width;
    this.height = height;
  }

  public void changeSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void changePrimaryImage(boolean primaryImage) {
    this.primaryImage = primaryImage;
  }

  public UUID getId() {
    return id;
  }

  public String getProductId() {
    return productId;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getByteSize() {
    return byteSize;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isPrimaryImage() {
    return primaryImage;
  }

  public String getAltPl() {
    return altPl;
  }

  public String getAltEn() {
    return altEn;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
