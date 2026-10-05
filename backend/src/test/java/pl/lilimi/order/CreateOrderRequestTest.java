package pl.lilimi.order;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderContactRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateOrderRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  void shouldAcceptDigitalOrderWithoutAddress() {
    var request = new CreateOrderRequest(
      "pl",
      validContact(),
      new OrderDeliveryRequest.Digital(),
      List.of(new OrderItemRequest.Digital("forest-dragon", 1))
    );

    assertTrue(validator.validate(request).isEmpty());
  }

  @Test
  void shouldRejectEmptyOrder() {
    var request = new CreateOrderRequest(
      "pl",
      validContact(),
      new OrderDeliveryRequest.Digital(),
      List.of()
    );

    assertTrue(validator.validate(request).stream()
      .anyMatch(error -> error.getPropertyPath().toString()
        .equals("items")));
  }

  @Test
  void shouldRejectInvalidNestedEmail() {
    var request = new CreateOrderRequest(
      "pl",
      new OrderContactRequest("Test Customer", "invalid-email", null),
      new OrderDeliveryRequest.Digital(),
      List.of(new OrderItemRequest.Digital("forest-dragon", 1))
    );

    assertTrue(validator.validate(request).stream()
      .anyMatch(error -> error.getPropertyPath().toString()
        .equals("contact.email")));
  }

  @Test
  void shouldRejectCourierDeliveryWithoutAddress() {
    var request = new CreateOrderRequest(
      "pl",
      validContact(),
      new OrderDeliveryRequest.Courier("dhl-courier", null),
      List.of(new OrderItemRequest.Digital("forest-dragon", 1))
    );

    assertTrue(validator.validate(request).stream()
      .anyMatch(error -> error.getPropertyPath().toString()
        .equals("delivery.address")));
  }

  @Test
  void shouldRejectDigitalQuantityGreaterThanOne() {
    var request = new CreateOrderRequest(
      "pl",
      validContact(),
      new OrderDeliveryRequest.Digital(),
      List.of(new OrderItemRequest.Digital("forest-dragon", 2))
    );

    assertFalse(validator.validate(request).isEmpty());
  }

  private static OrderContactRequest validContact() {
    return new OrderContactRequest(
      "Test Customer",
      "customer@example.com",
      null
    );
  }
}
