package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductImage;

import java.util.List;

public record AdminProductImagesResponse(
  String productId,
  long version,
  List<AdminProductImageResponse> images
) {

  public AdminProductImagesResponse {
    images = List.copyOf(images);
  }

  public static AdminProductImagesResponse from(
    Product product,
    List<ProductImage> images
  ) {
    return new AdminProductImagesResponse(
      product.getId(),
      product.getVersion(),
      images.stream()
        .map(AdminProductImageResponse::from)
        .toList()
    );
  }
}
