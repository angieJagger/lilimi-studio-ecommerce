package pl.lilimi.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private Order order;

  @Column(name = "position", nullable = false)
  private int position;

  @Column(name = "kind", length = 32, nullable = false)
  private String kind;

  @Column(name = "product_id", length = 64, nullable = false)
  private String productId;

  @Column(name = "product_name", length = 200, nullable = false)
  private String productName;

  @Column(name = "pattern_id", length = 64)
  private String patternId;

  @Column(name = "pattern_name", length = 200)
  private String patternName;

  @Column(name = "fit", length = 16)
  private String fit;

  @Column(name = "size", length = 8)
  private String size;

  @Column(name = "color", length = 16)
  private String color;

  @Column(name = "embroidery_option_id", length = 32)
  private String embroideryOptionId;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "unit_price_in_grosz", nullable = false)
  private long unitPriceInGrosz;

  @Column(name = "line_total_in_grosz", nullable = false)
  private long lineTotalInGrosz;

  protected OrderItem() {
    // Required by JPA.
  }

  private OrderItem(
    Order order,
    int position,
    String kind,
    String productId,
    String productName,
    int quantity,
    long unitPriceInGrosz
  ) {
    if (order == null || position < 1) {
      throw new IllegalArgumentException("Invalid order item reference");
    }

    if (quantity < 1 || quantity > 99 || unitPriceInGrosz <= 0) {
      throw new IllegalArgumentException("Invalid order item amounts");
    }

    this.order = order;
    this.position = position;
    this.kind = kind;
    this.productId = productId;
    this.productName = productName;
    this.quantity = quantity;
    this.unitPriceInGrosz = unitPriceInGrosz;
    this.lineTotalInGrosz = Math.multiplyExact(
      unitPriceInGrosz,
      quantity
    );
  }

  public static OrderItem digital(
    Order order,
    int position,
    String productId,
    String productName,
    long unitPriceInGrosz
  ) {
    return new OrderItem(
      order,
      position,
      "digital",
      productId,
      productName,
      1,
      unitPriceInGrosz
    );
  }

  public static OrderItem sweatshirt(
    Order order,
    int position,
    String productId,
    String productName,
    String patternId,
    String patternName,
    String fit,
    String size,
    String color,
    String embroideryOptionId,
    int quantity,
    long unitPriceInGrosz
  ) {
    var item = new OrderItem(
      order,
      position,
      "sweatshirt",
      productId,
      productName,
      quantity,
      unitPriceInGrosz
    );

    item.patternId = patternId;
    item.patternName = patternName;
    item.fit = fit;
    item.size = size;
    item.color = color;
    item.embroideryOptionId = embroideryOptionId;

    return item;
  }

  public UUID getId() {
    return id;
  }

  public Order getOrder() {
    return order;
  }

  public int getPosition() {
    return position;
  }

  public String getKind() {
    return kind;
  }

  public String getProductId() {
    return productId;
  }

  public String getProductName() {
    return productName;
  }

  public String getPatternId() {
    return patternId;
  }

  public String getPatternName() {
    return patternName;
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

  public String getEmbroideryOptionId() {
    return embroideryOptionId;
  }

  public int getQuantity() {
    return quantity;
  }

  public long getUnitPriceInGrosz() {
    return unitPriceInGrosz;
  }

  public long getLineTotalInGrosz() {
    return lineTotalInGrosz;
  }
}
