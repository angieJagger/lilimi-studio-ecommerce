package pl.lilimi.order.application;

import org.springframework.stereotype.Service;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.domain.OrderDelivery;

import java.util.List;

@Service
public class OrderDeliveryService {

  public OrderDeliveryQuote quote(
    OrderDeliveryRequest request,
    List<PricedOrderItem> items
  ) {
    if (items.isEmpty()) {
      throw new InvalidOrderDeliveryException(
        "Cannot determine delivery for an empty order"
      );
    }

    boolean requiresShipping = items.stream()
      .anyMatch(item ->
        item.item() instanceof OrderItemRequest.Sweatshirt
      );

    if (!requiresShipping) {
      if (!(request instanceof OrderDeliveryRequest.Digital)) {
        throw new InvalidOrderDeliveryException(
          "Digital-only orders require digital delivery"
        );
      }

      return new OrderDeliveryQuote(OrderDelivery.digital(), 0);
    }

    if (!(request instanceof OrderDeliveryRequest.Courier courier)) {
      throw new InvalidOrderDeliveryException(
        "Orders containing physical products require courier delivery"
      );
    }

    long priceInGrosz = switch (courier.methodId()) {
      case "dhl-courier", "dpd-courier", "inpost-courier" -> 1200L;
      default -> throw new InvalidOrderDeliveryException(
        "Unsupported delivery method"
      );
    };

    var address = courier.address();

    if (address == null) {
      throw new InvalidOrderDeliveryException(
        "Courier delivery requires an address"
      );
    }

    var delivery = OrderDelivery.courier(
      courier.methodId(),
      address.addressLine1().trim(),
      optionalText(address.addressLine2()),
      address.postalCode().trim(),
      address.city().trim(),
      address.countryCode()
    );

    return new OrderDeliveryQuote(delivery, priceInGrosz);
  }

  private String optionalText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    return value.trim();
  }
}
