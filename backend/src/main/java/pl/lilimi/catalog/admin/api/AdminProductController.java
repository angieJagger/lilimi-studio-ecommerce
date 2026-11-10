package pl.lilimi.catalog.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.lilimi.catalog.admin.application.AdminProductQueryService;
import pl.lilimi.catalog.admin.application.AdminProductVisibilityService;
import pl.lilimi.catalog.admin.application.AdminProductTranslationService;
import pl.lilimi.catalog.admin.application.AdminProductPriceService;
import pl.lilimi.catalog.admin.application.AdminProductEditService;
import pl.lilimi.catalog.admin.application.AdminProductCreationService;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

  private final AdminProductQueryService queryService;
  private final AdminProductVisibilityService visibilityService;
  private final AdminProductTranslationService translationService;
  private final AdminProductPriceService priceService;
  private final AdminProductEditService editService;
  private final AdminProductCreationService creationService;

  public AdminProductController(
    AdminProductQueryService queryService,
    AdminProductVisibilityService visibilityService,
    AdminProductTranslationService translationService,
    AdminProductPriceService priceService,
    AdminProductEditService editService,
    AdminProductCreationService creationService
  ) {
    this.queryService = queryService;
    this.visibilityService = visibilityService;
    this.translationService = translationService;
    this.priceService = priceService;
    this.editService = editService;
    this.creationService = creationService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminProductSummaryResponse> createProduct(
    @Valid @RequestPart("metadata") CreateProductDraftRequest request,
    @RequestPart(value = "images", required = false) List<MultipartFile> images,
    @RequestPart(value = "package", required = false) MultipartFile digitalPackage
  ) {
    var product = creationService.createProduct(request, images == null ? List.of() : images, digitalPackage);
    return ResponseEntity
      .created(URI.create("/api/admin/products/" + product.id()))
      .body(product);
  }

  @GetMapping
  public AdminProductPageResponse findProducts(
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
  ) {
    return AdminProductPageResponse.from(
      queryService.findProducts(page, size)
    );
  }

  @GetMapping("/{id}")
  public AdminProductDetailsResponse findProduct(
    @PathVariable String id
  ) {
    return queryService.findProduct(id);
  }

  @PatchMapping("/{id}/translations")
  public AdminProductDetailsResponse updateTranslations(
    @PathVariable String id,
    @Valid @RequestBody UpdateProductTranslationsRequest request
  ) {
    return translationService.updateTranslations(id, request);
  }

  @PatchMapping("/{id}/visibility")
  public AdminProductSummaryResponse changeVisibility(
    @PathVariable String id,
    @Valid @RequestBody ChangeProductVisibilityRequest request
  ) {
    return visibilityService.changeVisibility(id, request);
  }

  @PatchMapping("/{id}/digital-price")
  public AdminProductDetailsResponse updateDigitalPrice(
    @PathVariable String id,
    @Valid @RequestBody UpdateDigitalProductPriceRequest request
  ) {
    return priceService.updatePrice(id, request);
  }

  @PostMapping(value = "/{id}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SaveProductResponse saveProduct(
    @PathVariable String id,
    @Valid @RequestPart("metadata") SaveProductRequest request,
    @RequestPart(value = "files", required = false) List<MultipartFile> files
  ) {
    return editService.save(id, request, files == null ? List.of() : files);
  }
}
