package pl.lilimi.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

  private final ProductRepository productRepository;
  private final ProductTranslationRepository translationRepository;
  private final ProductImageRepository imageRepository;
  private final GarmentVariantRepository variants;

  public ProductService(
    ProductRepository productRepository,
    ProductTranslationRepository translationRepository,
    ProductImageRepository imageRepository,
    GarmentVariantRepository variants
  ) {
    this.productRepository = productRepository;
    this.translationRepository = translationRepository;
    this.imageRepository = imageRepository;
    this.variants = variants;
  }

  public List<ProductResponse> getActiveProducts() {
    List<Product> products =
      productRepository.findAllByActiveTrueOrderByIdAsc();

    if (products.isEmpty()) {
      return List.of();
    }

    List<String> productIds = products.stream()
      .map(Product::getId)
      .toList();

    Map<String, List<ProductTranslation>> translationsByProduct =
      translationRepository.findAllByIdProductIdIn(productIds)
        .stream()
        .collect(Collectors.groupingBy(
          translation -> translation.getId().getProductId()
        ));

    Map<String, ProductPriceView> pricesByProduct =
      productRepository.findCatalogPrices(productIds)
        .stream()
        .collect(Collectors.toMap(
          ProductPriceView::getProductId,
          price -> price
        ));

    Map<String, List<String>> fileFormatsByProduct =
      getFileFormatsByProduct(productIds);

    var imagesByProduct = imageRepository.findAllByProductIdInOrderBySortOrderAscIdAsc(productIds)
      .stream().collect(Collectors.groupingBy(ProductImage::getProductId));

    return products.stream()
      .filter(product -> pricesByProduct.containsKey(product.getId()))
      .map(product -> toResponse(
        product,
        translationsByProduct.getOrDefault(
          product.getId(),
          List.of()
        ),
        pricesByProduct.get(product.getId()),
        fileFormatsByProduct.getOrDefault(
          product.getId(),
          List.of()
        ),
        imagesByProduct.getOrDefault(product.getId(), List.of())
      ))
      .toList();
  }

  public Optional<Integer> getQuantityLimit(String slug, long requestedQuantity, UUID variantId) {
    var product = productRepository.findBySlugAndActiveTrue(slug)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product unavailable"));
    if (variantId != null) {
      var variant = variants.findByIdAndProductId(variantId, product.getId())
        .filter(GarmentVariant::isActive)
        .filter(value -> productRepository.findByIdAndActiveTrue(value.getPatternId()).isPresent())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant unavailable"));
      return Optional.of(variant.getStockQuantity()).filter(quantity -> quantity < requestedQuantity);
    }

    return Optional.ofNullable(product.getStockQuantity())
      .filter(quantity -> quantity < requestedQuantity);
  }

  public Optional<ProductResponse> getActiveProductBySlug(String slug) {
    return productRepository.findBySlugAndActiveTrue(slug)
      .flatMap(product -> {
        List<String> productIds = List.of(product.getId());

        Optional<ProductPriceView> price =
          productRepository.findCatalogPrices(productIds)
            .stream()
            .findFirst();

        if (price.isEmpty()) {
          return Optional.empty();
        }

        List<ProductTranslation> translations =
          translationRepository.findAllByIdProductIdIn(productIds);

        List<String> fileFormats = getFileFormatsByProduct(productIds)
          .getOrDefault(product.getId(), List.of());

        return Optional.of(toResponse(
          product,
          translations,
          price.get(),
          fileFormats,
          imageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(product.getId())
        ));
      });
  }

  public Optional<List<GarmentVariantResponse>> getActiveVariantsBySlug(
    String slug
  ) {
    List<GarmentVariantView> variants =
      productRepository.findActiveGarmentVariantsBySlug(slug);

    if (variants.isEmpty()) {
      return Optional.empty();
    }

    List<GarmentVariantResponse> response = variants.stream()
      .map(variant -> new GarmentVariantResponse(
        variant.getId(),
        variant.getPatternId(),
        variant.getFit(),
        variant.getSize(),
        variant.getColor(),
        variant.getEmbroideryOptionId(),
        variant.getWidthMm(),
        variant.getHeightMm(),
        variant.getPlacement(),
        variant.getPriceInGrosz(),
        "PLN"
      ))
      .toList();

    return Optional.of(response);
  }

  private Map<String, List<String>> getFileFormatsByProduct(
    List<String> productIds
  ) {
    return productRepository.findFileFormats(productIds)
      .stream()
      .collect(Collectors.groupingBy(
        ProductFileFormatView::getProductId,
        Collectors.mapping(
          ProductFileFormatView::getFileFormat,
          Collectors.toList()
        )
      ));
  }

  private ProductResponse toResponse(
    Product product,
    List<ProductTranslation> translations,
    ProductPriceView price,
    List<String> fileFormats,
    List<ProductImage> images
  ) {
    Map<String, String> names = new HashMap<>();
    Map<String, String> descriptions = new HashMap<>();

    for (ProductTranslation translation : translations) {
      String language = translation.getId().getLanguage();

      names.put(language, translation.getName());
      descriptions.put(language, translation.getDescription());
    }

    return new ProductResponse(
      product.getId(),
      product.getSlug(),
      product.getProductType().name().toLowerCase(Locale.ROOT),
      names,
      descriptions,
      price.getPriceInGrosz(),
      price.getPriceType(),
      "PLN",
      fileFormats,
      product.isMadeToOrder(),
      product.isPersonalizationAvailable(),
      images.stream()
        .sorted(Comparator.comparing(ProductImage::isPrimaryImage).reversed()
          .thenComparingInt(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
        .map(image -> ProductImageResponse.from(image, product.getVersion()))
        .toList(),
      product.getPhysicalConfiguration() == null
        ? List.of() : product.getPhysicalConfiguration().variantAttributes()
    );
  }
}
