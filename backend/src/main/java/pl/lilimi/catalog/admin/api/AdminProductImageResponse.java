package pl.lilimi.catalog.admin.api;

import pl.lilimi.catalog.ProductImage;

import java.util.UUID;

public record AdminProductImageResponse(
  UUID id,
  String contentType,
  long byteSize,
  int width,
  int height,
  int sortOrder,
  boolean primaryImage,
  String altPl,
  String altEn
) {

  public static AdminProductImageResponse from(ProductImage image) {
    return new AdminProductImageResponse(
      image.getId(),
      image.getContentType(),
      image.getByteSize(),
      image.getWidth(),
      image.getHeight(),
      image.getSortOrder(),
      image.isPrimaryImage(),
      image.getAltPl(),
      image.getAltEn()
    );
  }
}
