package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import pl.lilimi.order.api.OrderAddressRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.api.SweatshirtConfigurationRequest;
import pl.lilimi.order.application.InvalidOrderDeliveryException;
import pl.lilimi.order.application.OrderDeliveryService;
import pl.lilimi.order.application.PricedOrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDeliveryServiceTest {

  private final OrderDeliveryService deliveryService =
    new OrderDeliveryService();

  @Test
  void shouldProvideFreeDigitalDelivery() {
    var quote = deliveryService.quote(
      new OrderDeliveryRequest.Digital(),
      List.of(digitalItem())
    );

    assertThat(quote.priceInGrosz()).isZero();
    assertThat(quote.delivery().getKind()).isEqualTo("digital");
    assertThat(quote.delivery().getAddressLine1()).isNull();
  }

  @Test
  void shouldPriceSupportedCouriersForMixedOrder() {
    for (var method : List.of(
      "dhl-courier",
      "dpd-courier",
      "inpost-courier"
    )) {
      var quote = deliveryService.quote(
        courier(method),
        List.of(digitalItem(), sweatshirtItem())
      );

      assertThat(quote.priceInGrosz()).isEqualTo(1200);
      assertThat(quote.delivery().getKind()).isEqualTo("courier");
      assertThat(quote.delivery().getMethodId()).isEqualTo(method);
      assertThat(quote.delivery().getAddressLine1())
        .isEqualTo("Testowa 10");
      assertThat(quote.delivery().getPostalCode())
        .isEqualTo("65-001");
      assertThat(quote.delivery().getCity())
        .isEqualTo("Zielona Góra");
      assertThat(quote.delivery().getAddressLine2()).isNull();
    }
  }

  @Test
  void shouldRejectDigitalDeliveryForPhysicalProduct() {
    assertThatThrownBy(() -> deliveryService.quote(
      new OrderDeliveryRequest.Digital(),
      List.of(sweatshirtItem())
    )).isInstanceOf(InvalidOrderDeliveryException.class);
  }

  @Test
  void shouldRejectCourierForDigitalOnlyOrder() {
    assertThatThrownBy(() -> deliveryService.quote(
      courier("dhl-courier"),
      List.of(digitalItem())
    )).isInstanceOf(InvalidOrderDeliveryException.class);
  }

  @Test
  void shouldRejectUnsupportedCourier() {
    assertThatThrownBy(() -> deliveryService.quote(
      courier("unknown-courier"),
      List.of(sweatshirtItem())
    )).isInstanceOf(InvalidOrderDeliveryException.class);
  }

  private OrderDeliveryRequest.Courier courier(String methodId) {
    return new OrderDeliveryRequest.Courier(
      methodId,
      new OrderAddressRequest(
        " Testowa 10 ",
        "   ",
        " 65-001 ",
        " Zielona Góra ",
        "PL"
      )
    );
  }

  private PricedOrderItem digitalItem() {
    return new PricedOrderItem(
      new OrderItemRequest.Digital("pattern-001", 1),
      "Leśny smok",
      null,
      2900
    );
  }

  private PricedOrderItem sweatshirtItem() {
    return new PricedOrderItem(
      new OrderItemRequest.Sweatshirt(
        "embroidered-002",
        "pattern-001",
        new SweatshirtConfigurationRequest(
          "men", "M", "black", "small-front"
        ),
        2
      ),
      "Bluza z haftem",
      "Leśny smok",
      14900
    );
  }
}
