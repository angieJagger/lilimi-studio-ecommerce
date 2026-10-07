package pl.lilimi.order.application.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.persistence.OrderRepository;

@Service
public class AdminOrderQueryService {

  private final OrderRepository orders;

  public AdminOrderQueryService(OrderRepository orders) {
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public Page<Order> findOrders(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid pagination");
    }

    var sorting = Sort.by(
      Sort.Order.desc("createdAt"),
      Sort.Order.desc("id")
    );

    return orders.findAll(PageRequest.of(page, size, sorting));
  }
}
