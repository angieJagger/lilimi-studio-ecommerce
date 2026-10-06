package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderContactRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.application.OrderService;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OrderTransactionTest {

  @Autowired
  private OrderService orderService;

  @Autowired
  private OrderRepository orderRepository;

  @MockitoSpyBean
  private OrderItemRepository orderItemRepository;

  @Test
  void shouldRollBackOrderWhenSavingItemsFails() {
    long ordersBefore = orderRepository.count();
    long itemsBefore = orderItemRepository.count();

    doAnswer(invocation -> {
      orderRepository.flush();

      assertThat(orderRepository.count())
        .isEqualTo(ordersBefore + 1);

      throw new IllegalStateException("Simulated item save failure");
    }).when(orderItemRepository).saveAll(anyIterable());

    var request = new CreateOrderRequest(
      "pl",
      new OrderContactRequest(
        "Test Customer",
        "customer@example.com",
        null
      ),
      new OrderDeliveryRequest.Digital(),
      List.of(
        new OrderItemRequest.Digital("pattern-001", 1)
      )
    );

    assertThatThrownBy(() -> orderService.createOrder(request))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Simulated item save failure");

    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    assertThat(orderItemRepository.count()).isEqualTo(itemsBefore);
  }
}
