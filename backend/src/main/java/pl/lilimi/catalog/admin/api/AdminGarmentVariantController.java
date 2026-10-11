package pl.lilimi.catalog.admin.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.catalog.admin.application.AdminGarmentVariantQueryService;
import pl.lilimi.catalog.admin.application.AdminGarmentVariantUpdateService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products/{productId}/variants")
public class AdminGarmentVariantController {

  private final AdminGarmentVariantQueryService queryService;
  private final AdminGarmentVariantUpdateService updateService;

  public AdminGarmentVariantController(
    AdminGarmentVariantQueryService queryService,
    AdminGarmentVariantUpdateService updateService
  ) {
    this.queryService = queryService;
    this.updateService = updateService;
  }

  @GetMapping
  public AdminGarmentVariantPageResponse findVariants(
    @PathVariable String productId,
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
  ) {
    return queryService.findVariants(productId, page, size);
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
