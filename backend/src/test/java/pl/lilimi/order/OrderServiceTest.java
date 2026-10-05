package pl.lilimi.order;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.*;
import pl.lilimi.order.application.InvalidOrderDeliveryException;
import pl.lilimi.order.application.OrderProductUnavailableException;
import pl.lilimi.order.application.OrderService;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderServiceTest {

  @Autowired
  private OrderService orderService;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private OrderItemRepository orderItemRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldCreateMixedOrderWithPricesFromCatalog() {
    var request = new CreateOrderRequest(
      "en",
      contact(),
      courier(),
      List.of(digital(), sweatshirt())
    );

    var createdOrder = orderService.createOrder(request);

    entityManager.flush();

    var orderId = createdOrder.getId();

    entityManager.clear();

    var savedOrder = orderRepository.findById(orderId)
      .orElseThrow();

    var savedItems = orderItemRepository
      .findByOrder_IdOrderByPositionAsc(orderId);

    assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
    assertThat(savedOrder.getCreatedAt()).isNotNull();
    assertThat(savedOrder.getContact().getFullName())
      .isEqualTo("Test Customer");
    assertThat(savedOrder.getContact().getPhone()).isNull();

    assertThat(savedOrder.getDelivery().getKind())
      .isEqualTo("courier");
    assertThat(savedOrder.getDelivery().getMethodId())
      .isEqualTo("dhl-courier");

    assertThat(savedOrder.getSubtotalInGrosz()).isEqualTo(32700);
    assertThat(savedOrder.getDeliveryPriceInGrosz()).isEqualTo(1200);
    assertThat(savedOrder.getTotalInGrosz()).isEqualTo(33900);

    assertThat(savedItems)
      .extracting(OrderItem::getPosition)
      .containsExactly(1, 2);

    assertThat(savedItems)
      .extracting(OrderItem::getUnitPriceInGrosz)
      .containsExactly(2900L, 14900L);

    assertThat(savedItems)
      .extracting(OrderItem::getQuantity)
      .containsExactly(1, 2);

    assertThat(savedItems.get(1).getEmbroideryOptionId())
      .isEqualTo("small-front");

    long itemsTotal = savedItems.stream()
      .mapToLong(OrderItem::getLineTotalInGrosz)
      .sum();

    assertThat(itemsTotal).isEqualTo(savedOrder.getSubtotalInGrosz());
  }

  @Test
  void shouldNotSaveOrderWithUnavailableProduct() {
    long ordersBefore = orderRepository.count();
    long itemsBefore = orderItemRepository.count();

    var request = new CreateOrderRequest(
      "pl",
      contact(),
      new OrderDeliveryRequest.Digital(),
      List.of(
        digital(),
        new OrderItemRequest.Digital("missing-product", 1)
      )
    );

    assertThatThrownBy(() -> orderService.createOrder(request))
      .isInstanceOf(OrderProductUnavailableException.class);

    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    assertThat(orderItemRepository.count()).isEqualTo(itemsBefore);
  }

  @Test
  void shouldNotSavePhysicalOrderWithDigitalDelivery() {
    long ordersBefore = orderRepository.count();
    long itemsBefore = orderItemRepository.count();

    var request = new CreateOrderRequest(
      "pl",
      contact(),
      new OrderDeliveryRequest.Digital(),
      List.of(sweatshirt())
    );

    assertThatThrownBy(() -> orderService.createOrder(request))
      .isInstanceOf(InvalidOrderDeliveryException.class);

    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    assertThat(orderItemRepository.count()).isEqualTo(itemsBefore);
  }

  private OrderContactRequest contact() {
    return new OrderContactRequest(
      " Test Customer ",
      "customer@example.com",
      "   "
    );
  }

  private OrderDeliveryRequest.Courier courier() {
    return new OrderDeliveryRequest.Courier(
      "dhl-courier",
      new OrderAddressRequest(
        "Testowa 10",
        null,
        "65-001",
        "Zielona Góra",
        "PL"
      )
    );
  }

  private OrderItemRequest.Digital digital() {
    return new OrderItemRequest.Digital("pattern-001", 1);
  }

  private OrderItemRequest.Sweatshirt sweatshirt() {
    return new OrderItemRequest.Sweatshirt(
      "embroidered-002",
      "pattern-001",
      new SweatshirtConfigurationRequest(
        "men", "M", "black", "small-front"
      ),
      2
    );
  }
}
