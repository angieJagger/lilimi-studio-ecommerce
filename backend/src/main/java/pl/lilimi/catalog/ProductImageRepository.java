package pl.lilimi.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository
  extends JpaRepository<ProductImage, UUID> {

  List<ProductImage> findAllByProductIdOrderBySortOrderAscIdAsc(
    String productId
  );

  Optional<ProductImage> findByIdAndProductId(
    UUID id,
    String productId
  );
  List<ProductImage> findAllByProductIdInOrderBySortOrderAscIdAsc(
    Collection<String> productIds
  );
}
