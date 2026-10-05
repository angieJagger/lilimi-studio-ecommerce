package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateOrderRequestJsonTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void shouldReadDigitalOrder() {
    var json = """
            {
              "language": "pl",
              "contact": {
                "fullName": "Test Customer",
                "email": "customer@example.com"
              },
              "delivery": {
                "kind": "digital"
              },
              "items": [
                {
                  "kind": "digital",
                  "productId": "forest-dragon",
                  "quantity": 1
                }
              ]
            }
            """;

    var request = mapper.readValue(json, CreateOrderRequest.class);

    assertEquals("pl", request.language());
    assertEquals("customer@example.com", request.contact().email());

    assertInstanceOf(
      OrderDeliveryRequest.Digital.class,
      request.delivery()
    );

    var item = assertInstanceOf(
      OrderItemRequest.Digital.class,
      request.items().getFirst()
    );

    assertEquals("forest-dragon", item.productId());
    assertEquals(1, item.quantity().intValue());
  }

  @Test
  void shouldReadSweatshirtOrderWithCourierDelivery() {
    var json = """
            {
              "language": "en",
              "contact": {
                "fullName": "Test Customer",
                "email": "customer@example.com"
              },
              "delivery": {
                "kind": "courier",
                "methodId": "dhl-courier",
                "address": {
                  "addressLine1": "Testowa 10",
                  "postalCode": "65-001",
                  "city": "Zielona Góra",
                  "countryCode": "PL"
                }
              },
              "items": [
                {
                  "kind": "sweatshirt",
                  "productId": "embroidered-sweatshirt",
                  "patternId": "forest-dragon",
                  "configuration": {
                    "fit": "unisex",
                    "size": "M",
                    "color": "black",
                    "embroideryOptionId": "small-front"
                  },
                  "quantity": 2
                }
              ]
            }
            """;

    var request = mapper.readValue(json, CreateOrderRequest.class);

    var delivery = assertInstanceOf(
      OrderDeliveryRequest.Courier.class,
      request.delivery()
    );

    assertEquals("dhl-courier", delivery.methodId());
    assertEquals("65-001", delivery.address().postalCode());

    var item = assertInstanceOf(
      OrderItemRequest.Sweatshirt.class,
      request.items().getFirst()
    );

    assertEquals("forest-dragon", item.patternId());
    assertEquals("M", item.configuration().size());
    assertEquals("small-front", item.configuration().embroideryOptionId());
    assertEquals(2, item.quantity().intValue());
  }

  @Test
  void shouldRejectUnknownDeliveryKind() {
    var json = """
            {
              "language": "pl",
              "contact": {
                "fullName": "Test Customer",
                "email": "customer@example.com"
              },
              "delivery": {
                "kind": "unknown"
              },
              "items": []
            }
            """;

    assertThrows(
      InvalidTypeIdException.class,
      () -> mapper.readValue(json, CreateOrderRequest.class)
    );
  }
}
