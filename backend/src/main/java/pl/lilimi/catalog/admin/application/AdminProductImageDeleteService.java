package pl.lilimi.catalog.admin.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductImagesResponse;
import pl.lilimi.catalog.media.ProductImageStorage;

import java.io.IOException;
import java.util.UUID;

@Service
@Validated
public class AdminProductImageDeleteService {

  private static final Logger log =
    LoggerFactory.getLogger(AdminProductImageDeleteService.class);

  private final ProductRepository products;
  private final ProductImageRepository images;
  private final ProductImageStorage storage;

  public AdminProductImageDeleteService(
    ProductRepository products,
    ProductImageRepository images,
    ProductImageStorage storage
  ) {
    this.products = products;
    this.images = images;
    this.storage = storage;
  }

  @Transactional
  public AdminProductImagesResponse delete(
    @NotBlank String productId,
    @NotNull UUID imageId,
    @PositiveOrZero long expectedVersion
  ) {
    var product = products.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getVersion() != expectedVersion) {
      throw new ProductVersionConflictException();
    }

    var productImages =
      images.findAllByProductIdOrderBySortOrderAscIdAsc(productId);

    var selectedImage = productImages.stream()
      .filter(image -> image.getId().equals(imageId))
      .findFirst()
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product image not found"
      ));

    // Detect concurrent changes before modifying the gallery.
    product.markImagesChanged();
    products.flush();

    images.delete(selectedImage);
    images.flush();

    var remainingImages = productImages.stream()
      .filter(image -> !image.getId().equals(imageId))
      .toList();

    if (selectedImage.isPrimaryImage() && !remainingImages.isEmpty()) {
      remainingImages.getFirst().changePrimaryImage(true);
      images.flush();
    }

    deleteFileAfterCommit(selectedImage.getStorageKey());

    return AdminProductImagesResponse.from(
      product,
      remainingImages
    );
  }

  private void deleteFileAfterCommit(String storageKey) {
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {

        @Override
        public void afterCommit() {
          try {
            storage.delete(storageKey);
          } catch (IOException exception) {
            log.error(
              "Could not delete image {} after transaction commit",
              storageKey,
              exception
            );
          }
        }
      }
    );
  }
}
