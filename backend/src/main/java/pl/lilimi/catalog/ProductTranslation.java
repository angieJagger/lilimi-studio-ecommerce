package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_translations")
public class ProductTranslation {

  @EmbeddedId
  private ProductTranslationId id;

  @Column(name = "name", length = 200, nullable = false)
  private String name;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  private String description;

  protected ProductTranslation() {
    // Required by JPA.
  }

  public void updateContent(String name, String description) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(
        "Product name is required"
      );
    }

    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException(
        "Product description is required"
      );
    }

    var normalizedName = name.trim();
    var normalizedDescription = description.trim();

    if (
      normalizedName.length() > 200 ||
        normalizedDescription.length() > 5000
    ) {
      throw new IllegalArgumentException(
        "Product translation exceeds the allowed length"
      );
    }

    this.name = normalizedName;
    this.description = normalizedDescription;
  }

  public ProductTranslationId getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }
}
