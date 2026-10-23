package pl.lilimi.order.application.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.order.persistence.OrderReadRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.util.UUID;

@Service
public class AdminOrderReadService {

  private final OrderRepository orders;
  private final OrderReadRepository reads;

  public AdminOrderReadService(
    OrderRepository orders,
    OrderReadRepository reads
  ) {
    this.orders = orders;
    this.reads = reads;
  }

  @Transactional
  public void markRead(UUID administratorId, UUID orderId) {
    if (!orders.existsById(orderId)) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Order not found"
      );
    }

    reads.markRead(administratorId, orderId);
  }
}
