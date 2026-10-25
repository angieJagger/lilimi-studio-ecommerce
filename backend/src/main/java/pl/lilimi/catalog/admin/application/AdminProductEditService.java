package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.DigitalProductPriceRepository;
import pl.lilimi.catalog.ProductImage;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductTranslation;
import pl.lilimi.catalog.ProductTranslationId;
import pl.lilimi.catalog.ProductTranslationRepository;
import pl.lilimi.catalog.ProductType;
import pl.lilimi.catalog.admin.api.AdminProductImagesResponse;
import pl.lilimi.catalog.admin.api.SaveProductRequest;
import pl.lilimi.catalog.admin.api.SaveProductResponse;
import pl.lilimi.catalog.media.ProductImageStorage;
import pl.lilimi.catalog.media.ProductImageValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
public class AdminProductEditService {
  private static final Logger log = LoggerFactory.getLogger(AdminProductEditService.class);
  private final ProductRepository products;
  private final ProductTranslationRepository translations;
  private final DigitalProductPriceRepository prices;
  private final ProductImageRepository images;
  private final ProductImageValidator validator;
  private final ProductImageStorage storage;
  private final AdminProductQueryService queryService;

  public AdminProductEditService(
    ProductRepository products, ProductTranslationRepository translations,
    DigitalProductPriceRepository prices, ProductImageRepository images,
    ProductImageValidator validator, ProductImageStorage storage,
    AdminProductQueryService queryService
  ) {
    this.products = products;
    this.translations = translations;
    this.prices = prices;
    this.images = images;
    this.validator = validator;
    this.storage = storage;
    this.queryService = queryService;
  }

  @Transactional
  public SaveProductResponse save(
    @NotBlank String productId, @NotNull @Valid SaveProductRequest request,
    @NotNull List<MultipartFile> files
  ) {
    var product = products.findById(productId).orElseThrow(() ->
      new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    if (product.getVersion() != request.expectedVersion()) {
      throw new ProductVersionConflictException();
    }
    if ((product.getProductType() == ProductType.DIGITAL)
      != (request.digitalPriceInGrosz() != null)) {
      throw invalid("A fixed price is required only for digital products");
    }
    var existing = images.findAllByProductIdOrderBySortOrderAscIdAsc(productId);
    var byId = existing.stream().collect(Collectors.toMap(ProductImage::getId, Function.identity()));
    var retainedIds = new HashSet<UUID>();
    var usedFiles = new HashSet<Integer>();
    int primaryCount = 0;
    for (var item : request.images()) {
      if (item.id() == null && item.fileIndex() == null) {
        throw invalid("A new image requires a file");
      }
      if (item.id() != null && (!byId.containsKey(item.id()) || !retainedIds.add(item.id()))) {
        throw invalid("Each existing image must belong to this product and occur once");
      }
      if (item.fileIndex() != null && (item.fileIndex() >= files.size() || !usedFiles.add(item.fileIndex()))) {
        throw invalid("Invalid or repeated image file index");
      }
      if (item.primaryImage()) primaryCount++;
    }
    if (usedFiles.size() != files.size() || (!request.images().isEmpty() && primaryCount != 1)) {
      throw invalid("Use every file once and select exactly one primary image");
    }
    var validated = files.stream().map(validator::validate).toList();
    var polish = translation(productId, "pl");
    var english = translation(productId, "en");
    var price = product.getProductType() == ProductType.DIGITAL
      ? prices.findById(productId).orElseThrow(() -> new ResponseStatusException(
          HttpStatus.CONFLICT, "Digital product price is missing"))
      : null;

    // Reserve the product version before touching files or related rows.
    product.markImagesChanged();
    products.flush();

    var newKeys = new ArrayList<String>();
    var obsoleteKeys = new ArrayList<String>();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { cleanup(obsoleteKeys); }
      @Override public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) cleanup(newKeys);
      }
    });

    polish.updateContent(request.pl().name(), request.pl().description());
    english.updateContent(request.en().name(), request.en().description());
    if (price != null) price.changePrice(request.digitalPriceInGrosz());

    // Flush removals and clear the unique primary flag before assigning it again.
    for (var image : existing) {
      if (!retainedIds.contains(image.getId())) {
        obsoleteKeys.add(image.getStorageKey());
        images.delete(image);
      } else {
        image.changePrimaryImage(false);
      }
    }
    images.flush();

    int position = 0;
    for (var item : request.images()) {
      ProductImage image = item.id() == null ? null : byId.get(item.id());
      if (item.fileIndex() != null) {
        var data = validated.get(item.fileIndex());
        String key;
        try {
          key = storage.store(data.content(), data.extension());
        } catch (IOException exception) {
          throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
            "Product images could not be stored", exception);
        }
        newKeys.add(key);
        if (image == null) {
          image = new ProductImage(UUID.randomUUID(), productId, key,
            data.contentType(), data.content().length, data.width(), data.height(),
            position, false, item.altPl().strip(), item.altEn().strip());
          image = images.save(image);
        } else {
          obsoleteKeys.add(image.getStorageKey());
          image.replaceFile(key, data.contentType(), data.content().length, data.width(), data.height());
        }
      }
      image.changeSortOrder(position++);
      image.changePrimaryImage(item.primaryImage());
      image.updateAlternativeText(item.altPl().strip(), item.altEn().strip());
    }
    images.flush();
    return new SaveProductResponse(queryService.findProduct(productId),
      AdminProductImagesResponse.from(product,
        images.findAllByProductIdOrderBySortOrderAscIdAsc(productId)));
  }

  private ProductTranslation translation(String productId, String language) {
    return translations.findById(new ProductTranslationId(productId, language))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
        "Required product translation is missing"));
  }

  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private void cleanup(List<String> keys) {
    for (var key : keys) {
      try { storage.delete(key); }
      catch (IOException exception) { log.error("Could not remove product image {}", key, exception); }
    }
  }
}
