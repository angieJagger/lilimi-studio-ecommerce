package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductTranslation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record AdminProductDetailsResponse(
  String id,
  long version,
  String slug,
  String productType,
  boolean active,
  boolean madeToOrder,
  boolean personalizationAvailable,
  List<AdminProductTranslationResponse> translations
) {

  public AdminProductDetailsResponse {
    translations = List.copyOf(translations);
  }

  public static AdminProductDetailsResponse from(
    Product product,
    List<ProductTranslation> translations
  ) {
    return new AdminProductDetailsResponse(
      product.getId(),
      product.getVersion(),
      product.getSlug(),
      product.getProductType().name().toLowerCase(Locale.ROOT),
      product.isActive(),
      product.isMadeToOrder(),
      product.isPersonalizationAvailable(),
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
