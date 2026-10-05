package pl.lilimi.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductTranslationRepository;
import pl.lilimi.catalog.ProductType;
import pl.lilimi.order.api.OrderItemRequest;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderPricingService {

  private final ProductRepository productRepository;
  private final ProductTranslationRepository translationRepository;

  public OrderPricingService(
    ProductRepository productRepository,
    ProductTranslationRepository translationRepository
  ) {
    this.productRepository = productRepository;
    this.translationRepository = translationRepository;
  }

  public List<PricedOrderItem> priceItems(
    List<OrderItemRequest> items,
    String language
  ) {
    return items.stream()
      .map(item -> switch (item) {
        case OrderItemRequest.Digital digital ->
          priceDigital(digital, language);
        case OrderItemRequest.Sweatshirt sweatshirt ->
          priceSweatshirt(sweatshirt, language);
      })
      .toList();
  }

  private PricedOrderItem priceDigital(
    OrderItemRequest.Digital item,
    String language
  ) {
    var product = requireProduct(item.productId(), ProductType.DIGITAL);

    var price = productRepository
      .findCatalogPrices(List.of(product.getId()))
      .stream()
      .filter(value -> "fixed".equals(value.getPriceType()))
      .findFirst()
      .orElseThrow(() ->
        new OrderProductUnavailableException(item.productId())
      );

    return new PricedOrderItem(
      item,
      requireName(product.getId(), language),
      null,
      price.getPriceInGrosz()
    );
  }

  private PricedOrderItem priceSweatshirt(
    OrderItemRequest.Sweatshirt item,
    String language
  ) {
    var product = requireProduct(
      item.productId(),
      ProductType.SWEATSHIRT
    );

    var pattern = requireProduct(
      item.patternId(),
      ProductType.DIGITAL
    );

    var configuration = item.configuration();

    var price = productRepository.findActiveSweatshirtPrice(
      product.getId(),
      pattern.getId(),
      configuration.fit(),
      configuration.size(),
      configuration.color(),
      configuration.embroideryOptionId()
    ).orElseThrow(() ->
      new OrderProductUnavailableException(item.productId())
    );

    return new PricedOrderItem(
      item,
      requireName(product.getId(), language),
      requireName(pattern.getId(), language),
      price
    );
  }

  private Product requireProduct(
    String productId,
    ProductType expectedType
  ) {
    return productRepository.findByIdAndActiveTrue(productId)
      .filter(product -> product.getProductType() == expectedType)
      .orElseThrow(() ->
        new OrderProductUnavailableException(productId)
      );
  }

  private String requireName(String productId, String language) {
    return translationRepository
      .findAllByIdProductIdIn(List.of(productId))
      .stream()
      .filter(translation ->
        language.equals(translation.getId().getLanguage())
      )
      .map(translation -> translation.getName())
      .filter(name -> name != null && !name.isBlank())
      .findFirst()
      .orElseThrow(() ->
        new OrderProductUnavailableException(productId)
      );
  }
}
