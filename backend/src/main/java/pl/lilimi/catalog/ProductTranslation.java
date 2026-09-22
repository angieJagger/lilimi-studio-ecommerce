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
