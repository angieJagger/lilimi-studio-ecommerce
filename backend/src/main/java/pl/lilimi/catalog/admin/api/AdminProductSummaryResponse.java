package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.Product;

import java.util.Locale;

public record AdminProductSummaryResponse(
  String id,
  long version,
  String slug,
  String productCode,
  String productType,
  boolean active,
  boolean madeToOrder,
  boolean personalizationAvailable,
  Long stockQuantity
) {

  public static AdminProductSummaryResponse from(Product product) {
    var quantity = product.getStockQuantity();
    return from(product, quantity == null ? null : quantity.longValue());
  }

  public static AdminProductSummaryResponse from(Product product, Long stockQuantity) {
    return new AdminProductSummaryResponse(
      product.getId(),
      product.getVersion(),
      product.getSlug(),
      product.getProductCode(),
      product.getProductType().name().toLowerCase(Locale.ROOT),
      product.isActive(),
      product.isMadeToOrder(),
      product.isPersonalizationAvailable(),
      stockQuantity
    );
  }
}
