package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductImagesResponse;
import pl.lilimi.catalog.admin.api.ReplaceProductImageRequest;
import pl.lilimi.catalog.media.ProductImageStorage;
import pl.lilimi.catalog.media.ProductImageValidator;

import java.io.IOException;
import java.util.UUID;

@Service
@Validated
public class AdminProductImageReplaceService {

  private static final Logger log =
    LoggerFactory.getLogger(AdminProductImageReplaceService.class);

  private final ProductRepository products;
  private final ProductImageRepository images;
  private final ProductImageValidator validator;
  private final ProductImageStorage storage;

  public AdminProductImageReplaceService(
    ProductRepository products,
    ProductImageRepository images,
    ProductImageValidator validator,
    ProductImageStorage storage
  ) {
    this.products = products;
    this.images = images;
    this.validator = validator;
    this.storage = storage;
  }

  @Transactional
  public AdminProductImagesResponse replace(
    @NotBlank String productId,
    @NotNull UUID imageId,
    @NotNull MultipartFile file,
    @NotNull @Valid ReplaceProductImageRequest request
  ) {
    var product = products.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getVersion() != request.expectedVersion()) {
      throw new ProductVersionConflictException();
    }

    var image = images.findByIdAndProductId(imageId, productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product image not found"
      ));

    var validated = validator.validate(file);
    String oldStorageKey = image.getStorageKey();

    // Detect concurrent changes before writing the replacement file.
    product.markImagesChanged();
    products.flush();

    String newStorageKey;

    try {
      newStorageKey = storage.store(
        validated.content(),
        validated.extension()
      );
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Replacement image could not be stored",
        exception
      );
    }

    registerFileCleanup(oldStorageKey, newStorageKey);

    image.replaceFile(
      newStorageKey,
      validated.contentType(),
      validated.content().length,
      validated.width(),
      validated.height()
    );

    images.flush();

    return AdminProductImagesResponse.from(
      product,
      images.findAllByProductIdOrderBySortOrderAscIdAsc(productId)
    );
  }

  private void registerFileCleanup(
    String oldStorageKey,
    String newStorageKey
  ) {
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {

        @Override
        public void afterCommit() {
          deleteFile(oldStorageKey, "after replacement commit");
        }

        @Override
        public void afterCompletion(int status) {
          if (status == STATUS_ROLLED_BACK) {
            deleteFile(newStorageKey, "after replacement rollback");
          }
        }
      }
    );
  }

  private void deleteFile(String storageKey, String operation) {
    try {
      storage.delete(storageKey);
    } catch (IOException exception) {
      log.error(
        "Could not delete image {} {}",
        storageKey,
        operation,
        exception
      );
    }
  }
}
