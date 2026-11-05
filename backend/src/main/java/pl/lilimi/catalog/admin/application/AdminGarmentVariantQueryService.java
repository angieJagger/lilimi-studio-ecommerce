package pl.lilimi.catalog.admin.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import pl.lilimi.catalog.admin.api.AdminEmbroiderySelectionResponse;
import pl.lilimi.catalog.admin.api.AdminGarmentVariantPageResponse;
import pl.lilimi.catalog.GarmentVariant;
import pl.lilimi.catalog.GarmentVariantRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductType;

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
    return findVariants(productId, page, size, null, null, null);
  }

  @Transactional(readOnly = true)
  public AdminGarmentVariantPageResponse findVariants(
    String productId,
    int page,
    int size,
    String fit,
    String patternId,
    String embroideryOptionId
  ) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException(
        "Page must be non-negative and size must be between 1 and 100"
      );
    }

    requireGarmentProduct(productId);

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

    Specification<GarmentVariant> specification =
      (root, query, builder) ->
        builder.equal(root.get("productId"), productId);

    if (fit != null && !fit.isBlank()) {
      specification = specification.and(
        (root, query, builder) ->
          builder.equal(root.get("fit"), fit.trim())
      );
    }

    if (patternId != null && !patternId.isBlank()) {
      specification = specification.and(
        (root, query, builder) ->
          builder.equal(root.get("patternId"), patternId.trim())
      );
    }

    if (embroideryOptionId != null && !embroideryOptionId.isBlank()) {
      specification = specification.and(
        (root, query, builder) ->
          builder.equal(
            root.get("embroideryOptionId"),
            embroideryOptionId.trim()
          )
      );
    }

    return AdminGarmentVariantPageResponse.from(
      variants.findAll(specification, pageable)
    );
  }

  @Transactional(readOnly = true)
  public List<AdminEmbroiderySelectionResponse> findEmbroiderySelections(
    String productId
  ) {
    requireGarmentProduct(productId);

    return variants.findEmbroiderySelectionsByProductId(productId)
      .stream()
      .map(selection -> new AdminEmbroiderySelectionResponse(
        selection.getPatternId(),
        selection.getEmbroideryOptionId()
      ))
      .toList();
  }

  private void requireGarmentProduct(String productId) {
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
  }
}
