package pl.lilimi.catalog;

import java.util.Map;
import java.util.UUID;

public record ProductImageResponse(
  UUID id, int width, int height, boolean primaryImage,
  Map<String, String> alt, long version
) {
  public ProductImageResponse { alt = Map.copyOf(alt); }

  public static ProductImageResponse from(ProductImage image, long version) {
    return new ProductImageResponse(image.getId(), image.getWidth(), image.getHeight(),
      image.isPrimaryImage(), Map.of("pl", image.getAltPl(), "en", image.getAltEn()), version);
  }
}
