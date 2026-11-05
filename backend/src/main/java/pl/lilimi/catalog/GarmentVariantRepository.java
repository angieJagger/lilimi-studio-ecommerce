package pl.lilimi.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  interface EmbroiderySelection {

    String getPatternId();

    String getEmbroideryOptionId();
  }
}
