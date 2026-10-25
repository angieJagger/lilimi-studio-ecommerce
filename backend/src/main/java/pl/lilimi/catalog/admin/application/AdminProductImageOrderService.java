package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductImage;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductImagesResponse;
import pl.lilimi.catalog.admin.api.ReorderProductImagesRequest;

import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
public class AdminProductImageOrderService {

  private final ProductRepository products;
  private final ProductImageRepository images;

  public AdminProductImageOrderService(
    ProductRepository products,
    ProductImageRepository images
  ) {
    this.products = products;
    this.images = images;
  }

  @Transactional
  public AdminProductImagesResponse reorder(
    @NotBlank String productId,
    @NotNull @Valid ReorderProductImagesRequest request
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

    var imagesById = productImages.stream()
      .collect(Collectors.toMap(
        ProductImage::getId,
        Function.identity()
      ));

    var requestedIds = new HashSet<>(request.imageIds());

    if (
      requestedIds.size() != request.imageIds().size()
        || !requestedIds.equals(imagesById.keySet())
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Provide every product image exactly once"
      );
    }

    // Detect concurrent changes before modifying image positions.
    product.markImagesChanged();
    products.flush();

    for (int index = 0; index < request.imageIds().size(); index++) {
      var imageId = request.imageIds().get(index);
      imagesById.get(imageId).changeSortOrder(index);
    }

    images.flush();

    return AdminProductImagesResponse.from(
      product,
      images.findAllByProductIdOrderBySortOrderAscIdAsc(productId)
    );
  }
}
