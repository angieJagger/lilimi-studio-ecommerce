package pl.lilimi.order.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import pl.lilimi.order.domain.Order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository
  extends JpaRepository<Order, UUID>,
  JpaSpecificationExecutor<Order> {

  Page<Order> findByCustomerAccountId(
    UUID customerAccountId,
    Pageable pageable
  );

  Optional<Order> findByIdAndCustomerAccountId(
    UUID id,
    UUID customerAccountId
  );

  @Query(value = """
    SELECT customer_order.id
    FROM orders customer_order
    WHERE EXISTS (
      SELECT 1 FROM order_items item
      WHERE item.order_id = customer_order.id AND item.stock_reserved
    )
    AND NOT EXISTS (
      SELECT 1 FROM order_payment_attempts payment
      WHERE payment.order_id = customer_order.id AND payment.status = 'succeeded'
    )
    AND (
      (customer_order.status = 'new' AND customer_order.created_at <= :cutoff AND (
        NOT customer_order.reservation_expiration_requested OR NOT EXISTS (
          SELECT 1 FROM order_payment_attempts payment
          WHERE payment.order_id = customer_order.id AND payment.status IN ('created', 'pending')
        )
      ))
      OR (
        customer_order.status = 'cancelled' AND NOT EXISTS (
          SELECT 1 FROM order_payment_attempts payment
          WHERE payment.order_id = customer_order.id AND payment.status IN ('created', 'pending')
        )
      )
    )
    ORDER BY customer_order.created_at, customer_order.id
    LIMIT 100
    """, nativeQuery = true)
  List<UUID> findDueStockReservationOrderIds(@Param("cutoff") Instant cutoff);

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
