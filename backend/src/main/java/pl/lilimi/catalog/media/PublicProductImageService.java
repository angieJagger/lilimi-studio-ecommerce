package pl.lilimi.catalog.media;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductImageRepository;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.UUID;

@Service
public class PublicProductImageService {
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final ProductImageStorage storage;

  public PublicProductImageService(ProductRepository products, ProductImageRepository images,
    ProductImageStorage storage) {
    this.products = products;
    this.images = images;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public PublicProductImageContent load(String slug, UUID imageId) {
    var product = products.findBySlugAndActiveTrue(slug).orElseThrow(this::notFound);
    // Apply the same availability rule as the public catalog, including garment variants.
    if (products.findCatalogPrices(List.of(product.getId())).isEmpty()) throw notFound();
    var image = images.findByIdAndProductId(imageId, product.getId()).orElseThrow(this::notFound);
    try {
      return new PublicProductImageContent(storage.load(image.getStorageKey()),
        image.getContentType(), image.getByteSize(), "\"" + imageId + "-" + product.getVersion() + "\"");
    } catch (NoSuchFileException exception) {
      throw notFound();
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image could not be loaded", exception);
    }
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product image not found");
  }
}
