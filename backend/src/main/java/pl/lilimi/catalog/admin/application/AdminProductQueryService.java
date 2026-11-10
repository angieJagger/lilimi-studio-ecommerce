package pl.lilimi.catalog.admin.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.GarmentVariantRepository;
import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.admin.api.AdminProductSummaryResponse;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductTranslationRepository;
import pl.lilimi.catalog.admin.api.AdminProductDetailsResponse;
import pl.lilimi.catalog.DigitalProductPrice;
import pl.lilimi.catalog.DigitalProductPriceRepository;
import pl.lilimi.catalog.ProductType;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminProductQueryService {

  private final ProductRepository repository;
  private final ProductTranslationRepository translations;

  private final DigitalProductPriceRepository digitalPrices;
  private final GarmentVariantRepository variants;

  public AdminProductQueryService(
    ProductRepository repository,
    ProductTranslationRepository translations,
    DigitalProductPriceRepository digitalPrices,
    GarmentVariantRepository variants
  ) {
    this.repository = repository;
    this.translations = translations;
    this.digitalPrices = digitalPrices;
    this.variants = variants;
  }

  @Transactional(readOnly = true)
  public Page<AdminProductSummaryResponse> findProducts(int page, int size, boolean archived) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException(
        "Page must be non-negative and size must be between 1 and 100"
      );
    }

    var pageable = PageRequest.of(
      page,
      size,
      Sort.by(Sort.Direction.ASC, "id")
    );

    var products = repository.findAllByArchived(archived, pageable);
    var garmentIds = products.stream().filter(this::hasGarmentVariants).map(Product::getId).toList();
    if (garmentIds.isEmpty()) {
      return products.map(AdminProductSummaryResponse::from);
    }

    var stockTotals = variants.findAvailableStockTotals(garmentIds).stream().collect(Collectors.toMap(
      GarmentVariantRepository.StockTotal::getProductId,
      GarmentVariantRepository.StockTotal::getStockQuantity
    ));
    return products.map(product -> hasGarmentVariants(product)
      ? AdminProductSummaryResponse.from(product, stockTotals.getOrDefault(product.getId(), 0L))
      : AdminProductSummaryResponse.from(product));
  }

  public AdminProductSummaryResponse summarize(Product product) {
    if (!hasGarmentVariants(product)) {
      return AdminProductSummaryResponse.from(product);
    }
    var quantity = variants.findAvailableStockTotals(List.of(product.getId())).stream()
      .findFirst().map(GarmentVariantRepository.StockTotal::getStockQuantity).orElse(0L);
    return AdminProductSummaryResponse.from(product, quantity);
  }

  private boolean hasGarmentVariants(Product product) {
    return product.getProductType() == ProductType.SWEATSHIRT || product.getProductType() == ProductType.TSHIRT;
  }

  @Transactional(readOnly = true)
  public AdminProductDetailsResponse findProduct(String id) {
    var product = repository.findById(id).filter(value -> !value.isArchived())
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    var productTranslations =
      translations.findAllByIdProductIdIn(List.of(id));

    Integer digitalPriceInGrosz =
      product.getProductType() == ProductType.DIGITAL
        ? digitalPrices.findById(id)
        .map(DigitalProductPrice::getPriceInGrosz)
        .orElse(null)
        : null;

    return AdminProductDetailsResponse.from(
      product,
      productTranslations,
      digitalPriceInGrosz
    );
  }
}
