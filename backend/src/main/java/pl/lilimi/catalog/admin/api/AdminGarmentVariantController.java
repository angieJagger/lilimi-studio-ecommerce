package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import pl.lilimi.catalog.admin.application.AdminCatalogRemovalService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.catalog.admin.application.AdminGarmentVariantQueryService;
import pl.lilimi.catalog.admin.application.AdminGarmentVariantUpdateService;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/admin/products/{productId}/variants")
public class AdminGarmentVariantController {

  private final AdminGarmentVariantQueryService queryService;
  private final AdminGarmentVariantUpdateService updateService;
  private final AdminCatalogRemovalService removalService;

  public AdminGarmentVariantController(
    AdminGarmentVariantQueryService queryService,
    AdminGarmentVariantUpdateService updateService,
    AdminCatalogRemovalService removalService
  ) {
    this.queryService = queryService;
    this.updateService = updateService;
    this.removalService = removalService;
  }

  @DeleteMapping("/{variantId}")
  public AdminCatalogRemovalService.Result removeVariant(
    @PathVariable String productId,
    @PathVariable UUID variantId,
    @RequestParam @Min(0) long expectedVersion
  ) {
    return removalService.removeVariant(productId, variantId, expectedVersion);
  }

  @GetMapping
  public AdminGarmentVariantPageResponse findVariants(
    @PathVariable String productId,
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
    @RequestParam(required = false) String fit,
    @RequestParam(required = false) String patternId,
    @RequestParam(required = false) String embroideryOptionId
  ) {
    return queryService.findVariants(
      productId,
      page,
      size,
      fit,
      patternId,
      embroideryOptionId
    );
  }

  @GetMapping("/embroidery-selections")
  public List<AdminEmbroiderySelectionResponse> findEmbroiderySelections(
    @PathVariable String productId
  ) {
    return queryService.findEmbroiderySelections(productId);
  }

  @PatchMapping("/{variantId}")
  public AdminGarmentVariantResponse updateVariant(
    @PathVariable String productId,
    @PathVariable UUID variantId,
    @Valid @RequestBody UpdateGarmentVariantRequest request
  ) {
    return updateService.updateVariant(
      productId,
      variantId,
      request
    );
  }
}
