package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.GarmentVariantRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductType;
import pl.lilimi.catalog.admin.api.AdminGarmentVariantResponse;
import pl.lilimi.catalog.admin.api.UpdateGarmentVariantRequest;

import java.util.UUID;

@Service
@Validated
public class AdminGarmentVariantUpdateService {

  private final ProductRepository products;
  private final GarmentVariantRepository variants;

  public AdminGarmentVariantUpdateService(
    ProductRepository products,
    GarmentVariantRepository variants
  ) {
    this.products = products;
    this.variants = variants;
  }

  @Transactional
  public AdminGarmentVariantResponse updateVariant(
    @NotBlank String productId,
    @NotNull UUID variantId,
    @NotNull @Valid UpdateGarmentVariantRequest request
  ) {
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

    var variant = variants.findByIdAndProductId(
      variantId,
      productId
    ).orElseThrow(() -> new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Variant not found for this product"
    ));

    if (variant.getVersion() != request.expectedVersion()) {
      throw new GarmentVariantVersionConflictException();
    }

    variant.changePrice(request.priceInGrosz());
    variant.changeVisibility(request.active());

    variants.flush();

    return AdminGarmentVariantResponse.from(variant);
  }
}
