package pl.lilimi.catalog.admin.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.GarmentVariantRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductType;
import pl.lilimi.catalog.admin.api.AdminGarmentVariantPageResponse;

@Service
public class AdminGarmentVariantQueryService {

  private final ProductRepository products;
  private final GarmentVariantRepository variants;

  public AdminGarmentVariantQueryService(
    ProductRepository products,
    GarmentVariantRepository variants
  ) {
    this.products = products;
    this.variants = variants;
  }

  @Transactional(readOnly = true)
  public AdminGarmentVariantPageResponse findVariants(
    String productId,
    int page,
    int size
  ) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException(
        "Page must be non-negative and size must be between 1 and 100"
      );
    }

    var product = products.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (
      product.getProductType() != ProductType.SWEATSHIRT &&
        product.getProductType() != ProductType.TSHIRT
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "This product does not support garment variants"
      );
    }

    var pageable = PageRequest.of(
      page,
      size,
      Sort.by(
        "fit",
        "size",
        "color",
        "patternId",
        "embroideryOptionId",
        "id"
      )
    );

    return AdminGarmentVariantPageResponse.from(
      variants.findAllByProductId(productId, pageable)
    );
  }
}
