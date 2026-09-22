package pl.lilimi.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

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
}
