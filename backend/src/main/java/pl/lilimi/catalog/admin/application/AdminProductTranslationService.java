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
import pl.lilimi.catalog.ProductTranslation;
import pl.lilimi.catalog.ProductTranslationId;
import pl.lilimi.catalog.ProductTranslationRepository;
import pl.lilimi.catalog.admin.api.AdminProductDetailsResponse;
import pl.lilimi.catalog.admin.api.UpdateProductTranslationsRequest;
import pl.lilimi.catalog.DigitalProductPrice;
import pl.lilimi.catalog.DigitalProductPriceRepository;
import pl.lilimi.catalog.ProductType;

import java.util.List;

@Service
@Validated
public class AdminProductTranslationService {

  private final ProductRepository products;
  private final ProductTranslationRepository translations;

  private final DigitalProductPriceRepository digitalPrices;

  public AdminProductTranslationService(
    ProductRepository products,
    ProductTranslationRepository translations,
    DigitalProductPriceRepository digitalPrices
  ) {
    this.products = products;
    this.translations = translations;
    this.digitalPrices = digitalPrices;
  }

  @Transactional
  public AdminProductDetailsResponse updateTranslations(
    @NotBlank String id,
    @NotNull @Valid UpdateProductTranslationsRequest request
  ) {
    var product = products.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getVersion() != request.expectedVersion()) {
      throw new ProductVersionConflictException();
    }

    var polish = findTranslation(id, "pl");
    var english = findTranslation(id, "en");

    polish.updateContent(
      request.pl().name(),
      request.pl().description()
    );

    english.updateContent(
      request.en().name(),
      request.en().description()
    );

    product.markTranslationsChanged();

    products.flush();

    Integer digitalPriceInGrosz =
      product.getProductType() == ProductType.DIGITAL
        ? digitalPrices.findById(id)
        .map(DigitalProductPrice::getPriceInGrosz)
        .orElse(null)
        : null;

    return AdminProductDetailsResponse.from(
      product,
      List.of(polish, english),
      digitalPriceInGrosz
    );
  }

  private ProductTranslation findTranslation(
    String productId,
    String language
  ) {
    return translations.findById(
      new ProductTranslationId(productId, language)
    ).orElseThrow(() -> new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Required product translation is missing"
    ));
  }
}
