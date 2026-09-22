package pl.lilimi.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductTranslationRepository
  extends JpaRepository<ProductTranslation, ProductTranslationId> {

  List<ProductTranslation> findAllByIdProductIdIn(
    Collection<String> productIds
  );
}
