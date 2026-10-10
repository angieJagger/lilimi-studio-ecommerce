package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.ProductTranslation;

public record AdminProductTranslationResponse(
  String language,
  String name,
  String description
) {

  public static AdminProductTranslationResponse from(
    ProductTranslation translation
  ) {
    return new AdminProductTranslationResponse(
      translation.getId().getLanguage(),
      translation.getName(),
      translation.getDescription()
    );
  }
}
