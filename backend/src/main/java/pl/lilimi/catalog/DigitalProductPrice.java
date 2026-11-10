package pl.lilimi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "digital_product_prices")
public class DigitalProductPrice {

  @Id
  @Column(name = "product_id", length = 64, nullable = false)
  private String productId;

  @Column(name = "price_in_grosz", nullable = false)
  private int priceInGrosz;

  protected DigitalProductPrice() {
    // Required by JPA.
  }

  public static DigitalProductPrice create(
    String productId,
    int priceInGrosz
  ) {
    var price = new DigitalProductPrice();
    price.productId = productId;
    price.changePrice(priceInGrosz);
    return price;
  }

  public String getProductId() {
    return productId;
  }

  public int getPriceInGrosz() {
    return priceInGrosz;
  }

  public void changePrice(int priceInGrosz) {
    if (priceInGrosz <= 0) {
      throw new IllegalArgumentException(
        "Price must be greater than zero"
      );
    }

    this.priceInGrosz = priceInGrosz;
  }
}
