package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductSummaryResponse;
import pl.lilimi.catalog.admin.api.ChangeProductVisibilityRequest;
import pl.lilimi.catalog.DigitalProductPriceRepository;
import pl.lilimi.catalog.GarmentVariantRepository;
import pl.lilimi.catalog.ProductTranslationId;
import pl.lilimi.catalog.ProductTranslationRepository;
import pl.lilimi.catalog.ProductType;
import pl.lilimi.catalog.digital.DigitalProductPackageRepository;

@Service
@Validated
public class AdminProductVisibilityService {

  private final ProductRepository repository;
  private final ProductTranslationRepository translations;
  private final DigitalProductPriceRepository prices;
  private final DigitalProductPackageRepository packages;
  private final GarmentVariantRepository variants;
  private final AdminProductQueryService queryService;

  public AdminProductVisibilityService(
    ProductRepository repository,
    ProductTranslationRepository translations,
    DigitalProductPriceRepository prices,
    DigitalProductPackageRepository packages,
    GarmentVariantRepository variants,
    AdminProductQueryService queryService
  ) {
    this.repository = repository;
    this.translations = translations;
    this.prices = prices;
    this.packages = packages;
    this.variants = variants;
    this.queryService = queryService;
  }

  @Transactional
  public AdminProductSummaryResponse changeVisibility(
    @NotBlank String id,
    @NotNull @Valid ChangeProductVisibilityRequest request
  ) {
    var product = repository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getVersion() != request.expectedVersion()) {
      throw new ProductVersionConflictException();
    }

    if (request.active() && !product.isActive()) {
      if (Integer.valueOf(0).equals(product.getStockQuantity())
        || (product.getProductType() == ProductType.PHYSICAL
          && (product.getPhysicalConfiguration() == null || product.getPhysicalConfiguration().priceInGrosz() == null
            || product.getStockQuantity() == null))) {
        throw new PhysicalProductNotPublishableException();
      }

      if (product.getProductType() == ProductType.DIGITAL) {
        requireReadyForPublication(id);
      }

      if (product.getProductType() == ProductType.SWEATSHIRT && !variants.existsSellableVariant(id)) {
        throw new SweatshirtNotReadyException();
      }
    }
    product.changeVisibility(request.active());

    repository.flush();

    return queryService.summarize(product);
  }

  private void requireReadyForPublication(String productId) {
    if (
      !translations.existsById(new ProductTranslationId(productId, "pl")) ||
        !translations.existsById(new ProductTranslationId(productId, "en")) ||
        !prices.existsById(productId) ||
        packages.findByProductIdAndCurrentPackageTrue(productId).isEmpty()
    ) {
      throw new DigitalProductNotReadyException();
    }
  }
}
