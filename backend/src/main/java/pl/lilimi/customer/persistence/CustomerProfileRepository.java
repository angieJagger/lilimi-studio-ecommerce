package pl.lilimi.customer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lilimi.customer.domain.CustomerProfile;

import java.util.UUID;

public interface CustomerProfileRepository
  extends JpaRepository<CustomerProfile, UUID> {
}
