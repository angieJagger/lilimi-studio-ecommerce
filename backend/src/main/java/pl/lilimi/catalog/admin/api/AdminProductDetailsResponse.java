package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.PhysicalDraftConfiguration;
import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductTranslation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record AdminProductDetailsResponse(
  String id,
  long version,
  String slug,
  String productCode,
  String productType,
  boolean active,
  boolean madeToOrder,
  boolean personalizationAvailable,
  Integer digitalPriceInGrosz,
  PhysicalDraftConfiguration physicalConfiguration,
  Integer stockQuantity,
  List<AdminProductTranslationResponse> translations
) {

  public AdminProductDetailsResponse {
    translations = List.copyOf(translations);
  }

  public static AdminProductDetailsResponse from(
    Product product,
    List<ProductTranslation> translations,
    Integer digitalPriceInGrosz
  ) {
    return new AdminProductDetailsResponse(
      product.getId(),
      product.getVersion(),
      product.getSlug(),
      product.getProductCode(),
      product.getProductType().name().toLowerCase(Locale.ROOT),
      product.isActive(),
      product.isMadeToOrder(),
      product.isPersonalizationAvailable(),
      digitalPriceInGrosz,
      product.getPhysicalConfiguration(),
      product.getStockQuantity(),
      translations.stream()
        .map(AdminProductTranslationResponse::from)
        .sorted(
          Comparator.comparing(
            AdminProductTranslationResponse::language
          )
        )
        .toList()
    );
  }
}
