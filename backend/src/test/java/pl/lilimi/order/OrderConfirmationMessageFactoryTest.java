package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.lilimi.order.application.OrderConfirmationMessageFactory;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderItem;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderConfirmationMessageFactoryTest {

  private final OrderConfirmationMessageFactory factory =
    new OrderConfirmationMessageFactory();

  @Test
  void shouldCreatePolishDigitalOrderConfirmation() {
    var order = savedOrder(
      "pl",
      OrderDelivery.digital(),
      2900,
      0
    );

    var item = OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Leśny smok",
      2900
    );

    var message = factory.create(order, List.of(item));

    assertThat(message.recipient())
      .isEqualTo("customer@example.com");

    assertThat(message.subject())
      .contains("przyjęliśmy zamówienie")
      .contains(order.getId().toString());

    assertThat(message.text())
      .contains("Numer zamówienia: " + order.getId())
      .contains("1. Leśny smok")
      .contains("Liczba sztuk: 1")
      .contains("29,00")
      .contains("wyłącznie produkty cyfrowe")
      .contains("Nie pobrano płatności")
      .doesNotContain("DOSTAWA KURIEREM");
  }

  @Test
  void shouldCreateEnglishDigitalOrderConfirmation() {
    var order = savedOrder(
      "en",
      OrderDelivery.digital(),
      2900,
      0
    );

    var item = OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Forest dragon",
      2900
    );

    var message = factory.create(order, List.of(item));

    assertThat(message.subject())
      .contains("order received")
      .contains(order.getId().toString());

    assertThat(message.text())
      .contains("Order reference: " + order.getId())
      .contains("1. Forest dragon")
      .contains("Quantity: 1")
      .contains("29.00")
      .contains("only digital products")
      .contains("No payment has been collected")
      .doesNotContain("DOSTAWA KURIEREM");
  }

  @Test
  void shouldIncludeGarmentConfigurationAndCourierAddress() {
    var delivery = OrderDelivery.courier(
      "dhl-courier",
      "Testowa 12",
      null,
      "00-001",
      "Warszawa",
      "PL"
    );

    var order = savedOrder("pl", delivery, 59800, 1200);

    var item = OrderItem.sweatshirt(
      order,
      1,
      "sweatshirt-001",
      "Bluza z haftem",
      "pattern-001",
      "Leśny smok",
      "women",
      "M",
      "black",
      "small-front",
      2,
      29900
    );

    var message = factory.create(order, List.of(item));

    assertThat(message.text())
      .contains("Wzór haftu: Leśny smok")
      .contains("Krój: Damski")
      .contains("Rozmiar: M")
      .contains("Kolor: Czarny")
      .contains("Haft: Mały haft z przodu")
      .contains("Liczba sztuk: 2")
      .contains("Cena za sztukę: 299,00")
      .contains("Wartość pozycji: 598,00")
      .contains("Dostawa: 12,00")
      .contains("Razem: 610,00")
      .contains("Przewoźnik: DHL")
      .contains("Adres: Testowa 12")
      .contains("Kod pocztowy: 00-001")
      .contains("Miejscowość: Warszawa")
      .contains("Kraj: Polska")
      .doesNotContain("Telefon:")
      .doesNotContain("Dodatkowe dane adresowe:");
  }

  @Test
  void shouldUseSavedItemNameAndPrice() {
    var order = savedOrder(
      "en",
      OrderDelivery.digital(),
      3333,
      0
    );

    var item = OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Name saved at checkout",
      3333
    );

    var message = factory.create(order, List.of(item));

    assertThat(message.text())
      .contains("Name saved at checkout")
      .contains("Unit price: 33.33")
      .contains("Item total: 33.33")
      .contains("Total: 33.33");
  }

  @Test
  void shouldKeepUserSuppliedValuesOnSingleLines() {
    var order = savedOrder(
      "en",
      OrderDelivery.digital(),
      2900,
      0
    );

    var item = OrderItem.digital(
      order,
      1,
      "pattern-001",
      "First line\nSecond line",
      2900
    );

    var message = factory.create(order, List.of(item));

    assertThat(message.text())
      .contains("1. First line Second line")
      .doesNotContain("First line\nSecond line");
  }

  @Test
  void shouldRejectConfirmationWithoutItems() {
    var order = savedOrder(
      "pl",
      OrderDelivery.digital(),
      2900,
      0
    );

    assertThatThrownBy(() ->
      factory.create(order, List.of())
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Order items must not be empty");
  }

  private Order savedOrder(
    String language,
    OrderDelivery delivery,
    long subtotal,
    long deliveryPrice
  ) {
    var order = new Order(
      language,
      new OrderContact(
        "Anna Kowalska",
        "customer@example.com",
        null
      ),
      delivery,
      subtotal,
      deliveryPrice
    );

    ReflectionTestUtils.setField(
      order,
      "id",
      UUID.randomUUID()
    );

    return order;
  }
}
