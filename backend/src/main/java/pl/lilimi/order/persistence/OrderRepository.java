package pl.lilimi.order.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.lilimi.order.domain.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository
  extends JpaRepository<Order, UUID> {

  Page<Order> findByCustomerAccountId(
    UUID customerAccountId,
    Pageable pageable
  );

  Optional<Order> findByIdAndCustomerAccountId(
    UUID id,
    UUID customerAccountId
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
    select customerOrder
    from Order customerOrder
    where customerOrder.id = :id
    """)
  Optional<Order> findByIdForUpdate(
    @Param("id") UUID id
  );
}
