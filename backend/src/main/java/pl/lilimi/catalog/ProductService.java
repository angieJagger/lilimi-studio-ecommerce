package pl.lilimi.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProductService {

  private final ProductRepository productRepository;
  private final ProductTranslationRepository translationRepository;

  public ProductService(
    ProductRepository productRepository,
    ProductTranslationRepository translationRepository
  ) {
    this.productRepository = productRepository;
    this.translationRepository = translationRepository;
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

    return products.stream()
      .filter(product -> pricesByProduct.containsKey(product.getId()))
      .map(product -> toResponse(
        product,
        translationsByProduct.getOrDefault(
          product.getId(),
          List.of()
        ),
        pricesByProduct.get(product.getId())
      ))
      .toList();
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

        return Optional.of(toResponse(
          product,
          translations,
          price.get()
        ));
      });
  }

  private ProductResponse toResponse(
    Product product,
    List<ProductTranslation> translations,
    ProductPriceView price
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
      "PLN"
    );
  }
}
