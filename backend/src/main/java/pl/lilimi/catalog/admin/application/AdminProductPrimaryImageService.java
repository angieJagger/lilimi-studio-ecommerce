package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductImagesResponse;
import pl.lilimi.catalog.admin.api.ChangePrimaryProductImageRequest;

import java.util.UUID;

@Service
@Validated
public class AdminProductPrimaryImageService {

  private final ProductRepository products;
  private final ProductImageRepository images;

  public AdminProductPrimaryImageService(
    ProductRepository products,
    ProductImageRepository images
  ) {
    this.products = products;
    this.images = images;
  }

  @Transactional
  public AdminProductImagesResponse changePrimaryImage(
    @NotBlank String productId,
    @NotNull UUID imageId,
    @NotNull @Valid ChangePrimaryProductImageRequest request
  ) {
    var product = products.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getVersion() != request.expectedVersion()) {
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

    // Detect concurrent product changes before modifying the gallery.
    product.markImagesChanged();
    products.flush();

    // Clear the previous primary image before assigning another one.
    productImages.forEach(image -> image.changePrimaryImage(false));
    images.flush();

    selectedImage.changePrimaryImage(true);
    images.flush();

    return AdminProductImagesResponse.from(
      product,
      productImages
    );
  }
}
