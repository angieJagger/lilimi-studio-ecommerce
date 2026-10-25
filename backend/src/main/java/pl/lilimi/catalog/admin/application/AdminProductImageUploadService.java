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
import pl.lilimi.catalog.ProductImage;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductImagesResponse;
import pl.lilimi.catalog.admin.api.UploadProductImageRequest;
import pl.lilimi.catalog.media.ProductImageStorage;
import pl.lilimi.catalog.media.ProductImageValidator;

import java.io.IOException;
import java.util.UUID;

@Service
@Validated
public class AdminProductImageUploadService {

  private static final Logger log =
    LoggerFactory.getLogger(AdminProductImageUploadService.class);

  private static final int MAX_IMAGES = 10;

  private final ProductRepository products;
  private final ProductImageRepository images;
  private final ProductImageValidator validator;
  private final ProductImageStorage storage;

  public AdminProductImageUploadService(
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
  public AdminProductImagesResponse upload(
    @NotBlank String productId,
    @NotNull MultipartFile file,
    @NotNull @Valid UploadProductImageRequest request
  ) {
    var product = products.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getVersion() != request.expectedVersion()) {
      throw new ProductVersionConflictException();
    }

    var existingImages =
      images.findAllByProductIdOrderBySortOrderAscIdAsc(productId);

    if (existingImages.size() >= MAX_IMAGES) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "A product can have at most 10 images"
      );
    }

    var validated = validator.validate(file);

    int sortOrder = existingImages.stream()
      .mapToInt(ProductImage::getSortOrder)
      .max()
      .orElse(-1) + 1;

    // Detect concurrent changes before writing a file.
    product.markImagesChanged();
    products.flush();

    String storageKey;

    try {
      storageKey = storage.store(
        validated.content(),
        validated.extension()
      );
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Image file could not be stored",
        exception
      );
    }

    deleteFileOnRollback(storageKey);

    var image = new ProductImage(
      UUID.randomUUID(),
      productId,
      storageKey,
      validated.contentType(),
      validated.content().length,
      validated.width(),
      validated.height(),
      sortOrder,
      existingImages.isEmpty(),
      request.altPl().strip(),
      request.altEn().strip()
    );

    images.saveAndFlush(image);

    return AdminProductImagesResponse.from(
      product,
      images.findAllByProductIdOrderBySortOrderAscIdAsc(productId)
    );
  }

  private void deleteFileOnRollback(String storageKey) {
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {

        @Override
        public void afterCompletion(int status) {
          if (status != STATUS_ROLLED_BACK) {
            return;
          }

          try {
            storage.delete(storageKey);
          } catch (IOException exception) {
            log.error(
              "Could not delete image {} after transaction rollback",
              storageKey,
              exception
            );
          }
        }
      }
    );
  }
}
