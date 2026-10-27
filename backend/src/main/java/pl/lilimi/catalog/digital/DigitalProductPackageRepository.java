package pl.lilimi.catalog.digital;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DigitalProductPackageRepository
  extends JpaRepository<DigitalProductPackage, UUID> {

  Optional<DigitalProductPackage>
  findByProductIdAndCurrentPackageTrue(String productId);

  List<DigitalProductPackage>
  findByProductIdOrderByPackageVersionDesc(String productId);

  @Query("""
    SELECT COALESCE(MAX(p.packageVersion), 0)
    FROM DigitalProductPackage p
    WHERE p.productId = :productId
    """)
  int findLatestVersion(@Param("productId") String productId);
}
