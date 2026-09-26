package pl.lilimi.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;



public interface ProductRepository extends JpaRepository<Product, String> {

  List<Product> findAllByActiveTrueOrderByIdAsc();

  Optional<Product> findBySlugAndActiveTrue(String slug);

  @Query(value = """
        SELECT
            p.id AS "productId",
            d.price_in_grosz AS "priceInGrosz",
            'fixed' AS "priceType"
        FROM products p
        JOIN digital_product_prices d ON d.product_id = p.id
        WHERE p.active = TRUE
          AND p.product_type = 'digital'
          AND p.id IN (:productIds)

        UNION ALL

        SELECT
            p.id AS "productId",
            MIN(v.price_in_grosz) AS "priceInGrosz",
            'from' AS "priceType"
        FROM products p
        JOIN garment_variants v ON v.product_id = p.id
        JOIN products pattern ON pattern.id = v.pattern_id
        WHERE p.active = TRUE
          AND p.product_type IN ('sweatshirt', 'tshirt')
          AND v.active = TRUE
          AND pattern.active = TRUE
          AND pattern.product_type = 'digital'
          AND p.id IN (:productIds)
        GROUP BY p.id
        """, nativeQuery = true)
  List<ProductPriceView> findCatalogPrices(
    @Param("productIds") Collection<String> productIds
  );

  @Query(value = """
        SELECT
            v.id AS "id",
            v.pattern_id AS "patternId",
            v.fit AS "fit",
            v.size AS "size",
            v.color AS "color",
            v.embroidery_option_id AS "embroideryOptionId",
            e.width_mm AS "widthMm",
            e.height_mm AS "heightMm",
            e.placement AS "placement",
            v.price_in_grosz AS "priceInGrosz"
        FROM garment_variants v
        JOIN products p ON p.id = v.product_id
        JOIN products pattern ON pattern.id = v.pattern_id
        JOIN embroidery_options e
            ON e.pattern_id = v.pattern_id
           AND e.option_id = v.embroidery_option_id
        WHERE p.slug = :slug
          AND p.active = TRUE
          AND p.product_type IN ('sweatshirt', 'tshirt')
          AND v.active = TRUE
          AND pattern.active = TRUE
          AND pattern.product_type = 'digital'
        ORDER BY
            v.fit,
            v.size,
            v.color,
            v.pattern_id,
            v.embroidery_option_id
        """, nativeQuery = true)
  List<GarmentVariantView> findActiveGarmentVariantsBySlug(
    @Param("slug") String slug
  );
}
