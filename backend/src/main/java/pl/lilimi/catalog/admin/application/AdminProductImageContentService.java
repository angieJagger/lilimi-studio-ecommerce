package pl.lilimi.catalog.admin.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.media.ProductImageContent;
import pl.lilimi.catalog.media.ProductImageStorage;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.UUID;

@Service
public class AdminProductImageContentService {

  private final ProductImageRepository images;
  private final ProductImageStorage storage;

  public AdminProductImageContentService(
    ProductImageRepository images,
    ProductImageStorage storage
  ) {
    this.images = images;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public ProductImageContent load(
    String productId,
    UUID imageId
  ) {
    var image = images.findByIdAndProductId(imageId, productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product image not found"
      ));

    try {
      return new ProductImageContent(
        storage.load(image.getStorageKey()),
        image.getContentType(),
        image.getByteSize()
      );
    } catch (NoSuchFileException exception) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Image file not found"
      );
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Image file could not be loaded",
        exception
      );
    }
  }
}
