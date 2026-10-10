package pl.lilimi.catalog.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

  private final AdminProductQueryService queryService;
  private final AdminProductVisibilityService visibilityService;
  private final AdminProductTranslationService translationService;

  public AdminProductController(
    AdminProductQueryService queryService,
    AdminProductVisibilityService visibilityService,
    AdminProductTranslationService translationService
  ) {
    this.queryService = queryService;
    this.visibilityService = visibilityService;
    this.translationService = translationService;
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
}
