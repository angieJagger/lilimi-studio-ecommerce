package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.Product;

import java.util.Locale;

public record AdminProductSummaryResponse(
  String id,
  long version,
  String slug,
  String productType,
  boolean active,
  boolean madeToOrder,
  boolean personalizationAvailable
) {

  public static AdminProductSummaryResponse from(Product product) {
    return new AdminProductSummaryResponse(
      product.getId(),
      product.getVersion(),
      product.getSlug(),
      product.getProductType().name().toLowerCase(Locale.ROOT),
      product.isActive(),
      product.isMadeToOrder(),
      product.isPersonalizationAvailable()
    );
  }
}
