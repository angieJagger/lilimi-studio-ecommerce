package pl.lilimi.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lilimi.order.domain.Order;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
