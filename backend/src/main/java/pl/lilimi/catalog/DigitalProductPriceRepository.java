package pl.lilimi.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DigitalProductPriceRepository
  extends JpaRepository<DigitalProductPrice, String> {
}
