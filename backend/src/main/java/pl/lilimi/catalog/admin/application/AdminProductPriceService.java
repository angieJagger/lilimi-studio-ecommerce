package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.DigitalProductPriceRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductType;
import pl.lilimi.catalog.admin.api.AdminProductDetailsResponse;
import pl.lilimi.catalog.admin.api.UpdateDigitalProductPriceRequest;

@Service
@Validated
public class AdminProductPriceService {

  private final ProductRepository products;
  private final DigitalProductPriceRepository prices;
  private final AdminProductQueryService queryService;

  public AdminProductPriceService(
    ProductRepository products,
    DigitalProductPriceRepository prices,
    AdminProductQueryService queryService
  ) {
    this.products = products;
    this.prices = prices;
    this.queryService = queryService;
  }

  @Transactional
  public AdminProductDetailsResponse updatePrice(
    @NotBlank String id,
    @NotNull @Valid UpdateDigitalProductPriceRequest request
  ) {
    var product = products.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getProductType() != ProductType.DIGITAL) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Only digital products have a fixed digital price"
      );
    }

    if (product.getVersion() != request.expectedVersion()) {
      throw new ProductVersionConflictException();
    }

    var price = prices.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Digital product price is missing"
      ));

    price.changePrice(request.priceInGrosz());
    product.markPriceChanged();

    products.flush();

    return queryService.findProduct(id);
  }
}
