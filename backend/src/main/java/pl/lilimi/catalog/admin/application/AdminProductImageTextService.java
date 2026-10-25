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
import pl.lilimi.catalog.admin.api.UpdateProductImageTextRequest;

import java.util.UUID;

@Service
@Validated
public class AdminProductImageTextService {

  private final ProductRepository products;
  private final ProductImageRepository images;

  public AdminProductImageTextService(
    ProductRepository products,
    ProductImageRepository images
  ) {
    this.products = products;
    this.images = images;
  }

  @Transactional
  public AdminProductImagesResponse updateText(
    @NotBlank String productId,
    @NotNull UUID imageId,
    @NotNull @Valid UpdateProductImageTextRequest request
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

    image.updateAlternativeText(
      request.altPl().strip(),
      request.altEn().strip()
    );

    product.markImagesChanged();
    products.flush();

    return AdminProductImagesResponse.from(
      product,
      images.findAllByProductIdOrderBySortOrderAscIdAsc(productId)
    );
  }
}
