package pl.lilimi.order;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.*;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderRepositoryTest {

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private OrderItemRepository orderItemRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSaveAndReadDigitalOrderWithItem() {
    var order = new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        "customer@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    );

    orderRepository.saveAndFlush(order);

    var item = OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Leśny smok",
      2900
    );

    orderItemRepository.saveAndFlush(item);

    var orderId = order.getId();

    entityManager.clear();

    var savedOrder = orderRepository.findById(orderId)
      .orElseThrow();

    var savedItems = orderItemRepository
      .findByOrder_IdOrderByPositionAsc(orderId);

    assertThat(savedOrder.getId()).isNotNull();
    assertThat(savedOrder.getCreatedAt()).isNotNull();
    assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
    assertThat(savedOrder.getLanguage()).isEqualTo("pl");
    assertThat(savedOrder.getCurrency()).isEqualTo("PLN");
    assertThat(savedOrder.getContact().getEmail())
      .isEqualTo("customer@example.com");

    assertThat(savedOrder.getDelivery().getKind())
      .isEqualTo("digital");
    assertThat(savedOrder.getDelivery().getAddressLine1()).isNull();

    assertThat(savedOrder.getSubtotalInGrosz()).isEqualTo(2900);
    assertThat(savedOrder.getDeliveryPriceInGrosz()).isZero();
    assertThat(savedOrder.getTotalInGrosz()).isEqualTo(2900);

    assertThat(savedItems).hasSize(1);

    var savedItem = savedItems.getFirst();

    assertThat(savedItem.getOrder().getId()).isEqualTo(orderId);
    assertThat(savedItem.getPosition()).isEqualTo(1);
    assertThat(savedItem.getKind()).isEqualTo("digital");
    assertThat(savedItem.getProductId()).isEqualTo("pattern-001");
    assertThat(savedItem.getProductName()).isEqualTo("Leśny smok");
    assertThat(savedItem.getQuantity()).isEqualTo(1);
    assertThat(savedItem.getUnitPriceInGrosz()).isEqualTo(2900);
    assertThat(savedItem.getLineTotalInGrosz()).isEqualTo(2900);
    assertThat(savedItem.getPatternId()).isNull();
  }

  @Test
  void shouldSaveMixedOrderWithCourierDeliveryAndOrderedItems() {
    var order = new Order(
      "en",
      new OrderContact(
        "Test Customer",
        "customer@example.com",
        "123456789"
      ),
      OrderDelivery.courier(
        "dhl-courier",
        "Testowa 10",
        "Lokal 2",
        "65-001",
        "Zielona Góra",
        "PL"
      ),
      32700,
      1200
    );

    orderRepository.saveAndFlush(order);

    var digitalItem = OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Forest Dragon embroidery pattern",
      2900
    );

    var sweatshirtItem = OrderItem.sweatshirt(
      order,
      2,
      "embroidered-002",
      "Embroidered sweatshirt",
      "pattern-001",
      "Forest Dragon embroidery pattern",
      "men",
      "M",
      "black",
      "small-front",
      2,
      14900
    );

    orderItemRepository.saveAllAndFlush(
      List.of(sweatshirtItem, digitalItem)
    );

    var orderId = order.getId();

    entityManager.clear();

    var savedOrder = orderRepository.findById(orderId)
      .orElseThrow();

    var savedItems = orderItemRepository
      .findByOrder_IdOrderByPositionAsc(orderId);

    assertThat(savedOrder.getContact().getPhone())
      .isEqualTo("123456789");

    var delivery = savedOrder.getDelivery();

    assertThat(delivery.getKind()).isEqualTo("courier");
    assertThat(delivery.getMethodId()).isEqualTo("dhl-courier");
    assertThat(delivery.getAddressLine1()).isEqualTo("Testowa 10");
    assertThat(delivery.getAddressLine2()).isEqualTo("Lokal 2");
    assertThat(delivery.getPostalCode()).isEqualTo("65-001");
    assertThat(delivery.getCity()).isEqualTo("Zielona Góra");
    assertThat(delivery.getCountryCode()).isEqualTo("PL");

    assertThat(savedOrder.getSubtotalInGrosz()).isEqualTo(32700);
    assertThat(savedOrder.getDeliveryPriceInGrosz()).isEqualTo(1200);
    assertThat(savedOrder.getTotalInGrosz()).isEqualTo(33900);

    assertThat(savedItems)
      .extracting(OrderItem::getPosition)
      .containsExactly(1, 2);

    var savedSweatshirt = savedItems.get(1);

    assertThat(savedSweatshirt.getKind()).isEqualTo("sweatshirt");
    assertThat(savedSweatshirt.getProductId())
      .isEqualTo("embroidered-002");
    assertThat(savedSweatshirt.getProductName())
      .isEqualTo("Embroidered sweatshirt");
    assertThat(savedSweatshirt.getPatternId()).isEqualTo("pattern-001");
    assertThat(savedSweatshirt.getPatternName())
      .isEqualTo("Forest Dragon embroidery pattern");
    assertThat(savedSweatshirt.getFit()).isEqualTo("men");
    assertThat(savedSweatshirt.getSize()).isEqualTo("M");
    assertThat(savedSweatshirt.getColor()).isEqualTo("black");
    assertThat(savedSweatshirt.getEmbroideryOptionId())
      .isEqualTo("small-front");
    assertThat(savedSweatshirt.getQuantity()).isEqualTo(2);
    assertThat(savedSweatshirt.getUnitPriceInGrosz()).isEqualTo(14900);
    assertThat(savedSweatshirt.getLineTotalInGrosz()).isEqualTo(29800);

    long itemsTotal = savedItems.stream()
      .mapToLong(OrderItem::getLineTotalInGrosz)
      .sum();

    assertThat(itemsTotal).isEqualTo(savedOrder.getSubtotalInGrosz());
  }
}
