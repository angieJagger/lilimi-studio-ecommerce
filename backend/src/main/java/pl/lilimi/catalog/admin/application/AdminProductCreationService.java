package pl.lilimi.catalog.admin.application;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.multipart.MultipartFile;
import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductType;
import pl.lilimi.catalog.admin.api.AdminProductSummaryResponse;
import pl.lilimi.catalog.admin.api.CreateProductDraftRequest;
import pl.lilimi.catalog.admin.api.SaveProductImageRequest;
import pl.lilimi.catalog.admin.api.SaveProductRequest;
import pl.lilimi.catalog.digital.AdminDigitalProductPackageService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Validated
public class AdminProductCreationService {

  private final EntityManager entityManager;
  private final ProductRepository repository;
  private final AdminProductEditService edits;
  private final AdminDigitalProductPackageService packages;

  public AdminProductCreationService(
    EntityManager entityManager,
    ProductRepository repository,
    AdminProductEditService edits,
    AdminDigitalProductPackageService packages
  ) {
    this.entityManager = entityManager;
    this.repository = repository;
    this.edits = edits;
    this.packages = packages;
  }

  @Transactional
  public AdminProductSummaryResponse createProduct(
    @NotNull @Valid CreateProductDraftRequest request,
    List<MultipartFile> images,
    MultipartFile digitalPackage
  ) {
    var productType = ProductType.valueOf(request.productType().toUpperCase(Locale.ROOT));
    if ((productType == ProductType.PHYSICAL) != (request.physicalConfiguration() != null)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Physical configuration must be supplied only for physical products"
      );
    }

    boolean digital = productType == ProductType.DIGITAL;
    if (digital) {
      if (request.digitalPriceInGrosz() == null || digitalPackage == null
        || digitalPackage.isEmpty() || request.stockQuantity() != null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A digital product requires a price and ZIP package");
      }
    } else if (request.physicalConfiguration().priceInGrosz() == null
      || request.stockQuantity() == null || digitalPackage != null || request.digitalPriceInGrosz() != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A physical product requires a price and stock quantity");
    }
    if (images.size() > 10) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product can have at most 10 images");
    }

    var productCode = request.productCode();

    if (productCode == null) {
      productCode = switch (productType) {
        case DIGITAL -> String.format(
          Locale.ROOT, "pattern-%03d", repository.nextDigitalProductCodeNumber()
        );
        case PHYSICAL -> String.format(
          Locale.ROOT, "physical-%03d", repository.nextPhysicalProductCodeNumber()
        );
        default -> throw new IllegalArgumentException("Unsupported product type");
      };
    }

    var slug = slugFromName(request.pl().name(), productCode);
    var product = Product.createDraft(slug, productCode, productType);
    if (productType == ProductType.PHYSICAL) {
      product.changePhysicalConfiguration(request.physicalConfiguration());
      product.changeStockQuantity(request.stockQuantity());
    }

    try {
      entityManager.persist(product);
      repository.flush();
    } catch (DataIntegrityViolationException exception) {
      if (violatesConstraint(exception, "uq_products_slug")) {
        throw new ProductSlugConflictException(exception);
      }

      if (violatesConstraint(exception, "uq_products_product_code")) {
        throw new ProductCodeConflictException(exception);
      }
      throw exception;
    }

    var imageRequests = new ArrayList<SaveProductImageRequest>();
    for (int index = 0; index < images.size(); index++) {
      imageRequests.add(new SaveProductImageRequest(null, index, request.pl().name(), request.en().name(), index == 0));
    }

    edits.save(product.getId(), new SaveProductRequest(
      product.getVersion(), request.pl(), request.en(), request.digitalPriceInGrosz(), null, null, imageRequests
    ), images);
    if (digital) {
      packages.upload(product.getId(), digitalPackage);
    }

    product.changeVisibility(digital || request.stockQuantity() > 0);
    repository.flush();
    return AdminProductSummaryResponse.from(product);
  }

  private String slugFromName(String name, String fallbackCode) {
    var slug = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
      .replace("ł", "l")
      .replaceAll("\\p{M}+", "")
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("^-|-$", "");

    if (slug.length() > 160) {
      slug = slug.substring(0, 160).replaceFirst("-$", "");
    }

    return slug.isEmpty() ? fallbackCode : slug;
  }

  private boolean violatesConstraint(Throwable exception, String constraintName) {
    Throwable cause = exception;

    while (cause != null) {
      if (cause instanceof ConstraintViolationException violation) {
        return constraintName.equals(violation.getConstraintName());
      }

      cause = cause.getCause();
    }

    return false;
  }
}
