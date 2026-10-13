package pl.lilimi.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Optional;

import pl.lilimi.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  Page<Order> findByCustomerAccountId(
    UUID customerAccountId,
    Pageable pageable
  );

  Optional<Order> findByIdAndCustomerAccountId(
    UUID id,
    UUID customerAccountId
  );
}
