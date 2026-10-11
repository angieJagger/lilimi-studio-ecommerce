package pl.lilimi.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GarmentVariantRepository
  extends JpaRepository<GarmentVariant, UUID> {

  Page<GarmentVariant> findAllByProductId(
    String productId,
    Pageable pageable
  );

  Optional<GarmentVariant> findByIdAndProductId(
    UUID id,
    String productId
  );
}
