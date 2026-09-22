package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProductTranslationId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "product_id", length = 64, nullable = false)
  private String productId;

  @Column(name = "language", length = 2, nullable = false)
  private String language;

  protected ProductTranslationId() {
    // Required by JPA.
  }

  public ProductTranslationId(String productId, String language) {
    this.productId = productId;
    this.language = language;
  }

  public String getProductId() {
    return productId;
  }

  public String getLanguage() {
    return language;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof ProductTranslationId that)) {
      return false;
    }

    return Objects.equals(productId, that.productId)
      && Objects.equals(language, that.language);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, language);
  }
}
