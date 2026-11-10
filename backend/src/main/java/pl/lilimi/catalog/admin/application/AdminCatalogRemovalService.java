package pl.lilimi.catalog.admin.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.GarmentVariant;
import pl.lilimi.catalog.GarmentVariantRepository;
import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductRepository;

import java.util.UUID;

@Service
public class AdminCatalogRemovalService {

  private final ProductRepository products;
  private final GarmentVariantRepository variants;
  private final JdbcTemplate jdbc;

  public AdminCatalogRemovalService(ProductRepository products, GarmentVariantRepository variants, JdbcTemplate jdbc) {
    this.products = products;
    this.variants = variants;
    this.jdbc = jdbc;
  }

  @Transactional
  public Result removeProduct(String productId, long expectedVersion) {
    var product = lockProduct(productId);
    if (product.getVersion() != expectedVersion) {
      throw new ProductVersionConflictException();
    }

    if (hasProductReferences(productId)) {
      product.archive();
      return new Result(true);
    }

    jdbc.update("DELETE FROM product_translations WHERE product_id = ?", productId);
    jdbc.update("DELETE FROM digital_product_prices WHERE product_id = ?", productId);
    jdbc.update("DELETE FROM product_file_formats WHERE product_id = ?", productId);
    jdbc.update("DELETE FROM embroidery_options WHERE pattern_id = ?", productId);
    products.delete(product);
    products.flush();
    return new Result(false);
  }

  @Transactional
  public Result removeVariant(String productId, UUID variantId, long expectedVersion) {
    lockProduct(productId);
    var variant = variants.findByIdAndProductId(variantId, productId)
      .filter(value -> !value.isArchived())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));

    if (variant.getVersion() != expectedVersion) {
      throw new GarmentVariantVersionConflictException();
    }

    if (hasVariantHistory(variant)) {
      variant.archive();
      return new Result(true);
    }

    variants.delete(variant);
    variants.flush();
    return new Result(false);
  }

  private Product lockProduct(String productId) {
    return products.findByIdForUpdate(productId)
      .filter(value -> !value.isArchived())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
  }

  private boolean hasProductReferences(String id) {
    // Keep media-backed entries to preserve file ownership and digital downloads.
    return Boolean.TRUE.equals(jdbc.queryForObject("""
      SELECT EXISTS (SELECT 1 FROM order_items WHERE product_id = ? OR pattern_id = ?)
          OR EXISTS (SELECT 1 FROM garment_variants WHERE product_id = ? OR pattern_id = ?)
          OR EXISTS (SELECT 1 FROM project_inquiries WHERE product_id = ?)
          OR EXISTS (SELECT 1 FROM product_images WHERE product_id = ?)
          OR EXISTS (SELECT 1 FROM digital_product_packages WHERE product_id = ?)
      """, Boolean.class, id, id, id, id, id, id, id));
  }

  private boolean hasVariantHistory(GarmentVariant variant) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
      SELECT EXISTS (
        SELECT 1 FROM order_items
        WHERE reserved_variant_id = ?
           OR (kind = 'sweatshirt' AND product_id = ? AND pattern_id = ?
               AND embroidery_option_id = ? AND fit = ? AND size = ? AND color = ?)
      )
      """, Boolean.class, variant.getId(), variant.getProductId(), variant.getPatternId(),
      variant.getEmbroideryOptionId(), variant.getFit(), variant.getSize(), variant.getColor()));
  }

  public record Result(boolean archived) {}
}
