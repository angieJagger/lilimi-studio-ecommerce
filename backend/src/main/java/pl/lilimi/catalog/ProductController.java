package pl.lilimi.catalog;

import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping
  public List<ProductResponse> getProducts() {
    return productService.getActiveProducts();
  }

  @GetMapping("/{slug}")
  public ResponseEntity<ProductResponse> getProduct(
    @PathVariable("slug") String slug
  ) {
    return ResponseEntity.of(
      productService.getActiveProductBySlug(slug)
    );
  }

  @GetMapping("/{slug}/quantity-check")
  public ResponseEntity<QuantityLimit> checkQuantity(
    @PathVariable("slug") String slug,
    @RequestParam @Min(1) long quantity,
    @RequestParam(required = false) UUID variantId
  ) {
    return productService.getQuantityLimit(slug, quantity, variantId)
      .map(limit -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new QuantityLimit(limit)))
      .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
  }

  public record QuantityLimit(int maxQuantity) {}

  @GetMapping("/{slug}/variants")
  public ResponseEntity<List<GarmentVariantResponse>> getVariants(
    @PathVariable("slug") String slug
  ) {
    return ResponseEntity.of(
      productService.getActiveVariantsBySlug(slug)
    );
  }
}
