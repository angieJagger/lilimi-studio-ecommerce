package pl.lilimi.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GarmentVariantRepository
  extends JpaRepository<GarmentVariant, UUID>,
  JpaSpecificationExecutor<GarmentVariant> {

  Page<GarmentVariant> findAllByProductId(
    String productId,
    Pageable pageable
  );

  Optional<GarmentVariant> findByIdAndProductId(
    UUID id,
    String productId
  );

  @Query("""
    select distinct
      variant.patternId as patternId,
      variant.embroideryOptionId as embroideryOptionId
    from GarmentVariant variant
    where variant.productId = :productId
    order by variant.patternId, variant.embroideryOptionId
    """)
  List<EmbroiderySelection> findEmbroiderySelectionsByProductId(
    @Param("productId") String productId
  );

  @Query(value = """
    SELECT EXISTS (
      SELECT 1
      FROM garment_variants variant
      JOIN products pattern ON pattern.id = variant.pattern_id
      WHERE variant.product_id = :productId
        AND variant.active = TRUE
        AND variant.stock_quantity > 0
        AND pattern.active = TRUE
        AND pattern.product_type = 'digital'
    )
    """, nativeQuery = true)
  boolean existsSellableVariant(@Param("productId") String productId);

  @Query(value = """
    SELECT variant.product_id AS "productId", SUM(variant.stock_quantity) AS "stockQuantity"
    FROM garment_variants variant
    JOIN products pattern ON pattern.id = variant.pattern_id
    WHERE variant.product_id IN (:productIds)
      AND variant.active = TRUE
      AND pattern.active = TRUE
      AND pattern.product_type = 'digital'
    GROUP BY variant.product_id
    """, nativeQuery = true)
  List<StockTotal> findAvailableStockTotals(@Param("productIds") Collection<String> productIds);

  interface StockTotal {
    String getProductId();

    long getStockQuantity();
  }

  interface EmbroiderySelection {

    String getPatternId();

    String getEmbroideryOptionId();
  }
}
