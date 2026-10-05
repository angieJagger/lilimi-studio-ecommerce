package pl.lilimi.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lilimi.order.domain.OrderItem;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

  List<OrderItem> findByOrder_IdOrderByPositionAsc(UUID orderId);
}
