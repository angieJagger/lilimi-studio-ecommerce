package pl.lilimi.catalog.admin.api;

public record SaveProductResponse(
  AdminProductDetailsResponse product,
  AdminProductImagesResponse gallery
) {}
