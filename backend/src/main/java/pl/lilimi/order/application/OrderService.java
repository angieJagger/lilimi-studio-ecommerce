package pl.lilimi.order.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.catalog.digital.DigitalProductPackageRepository;

import java.util.ArrayList;
import java.util.UUID;

@Service
@Validated
public class OrderService {

  private final OrderPricingService pricingService;
  private final OrderDeliveryService deliveryService;
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final DigitalProductPackageRepository digitalPackages;

  public OrderService(
    OrderPricingService pricingService,
    OrderDeliveryService deliveryService,
    OrderRepository orderRepository,
    OrderItemRepository orderItemRepository,
    DigitalProductPackageRepository digitalPackages
  ) {
    this.pricingService = pricingService;
    this.deliveryService = deliveryService;
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.digitalPackages = digitalPackages;
  }

  @Transactional
  public Order createOrder(@NotNull @Valid CreateOrderRequest request) {
    return createOrder(request, null);
  }

  @Transactional
  public Order createOrder(
    @NotNull @Valid CreateOrderRequest request,
    UUID customerAccountId
  ) {
    var pricedItems = pricingService.priceItems(
      request.items(),
      request.language()
    );

    var deliveryQuote = deliveryService.quote(
      request.delivery(),
      pricedItems
    );

    long subtotalInGrosz = pricedItems.stream()
      .mapToLong(PricedOrderItem::lineTotalInGrosz)
      .reduce(0L, Math::addExact);

    var contactRequest = request.contact();

    var contact = new OrderContact(
      contactRequest.fullName().trim(),
      contactRequest.email().trim(),
      optionalText(contactRequest.phone())
    );

    var order = new Order(
      request.language(),
      contact,
      deliveryQuote.delivery(),
      subtotalInGrosz,
      deliveryQuote.priceInGrosz(),
      customerAccountId
    );

    orderRepository.save(order);

    var orderItems = new ArrayList<OrderItem>();

    for (int index = 0; index < pricedItems.size(); index++) {
      var pricedItem = pricedItems.get(index);
      int position = index + 1;

      var orderItem = switch (pricedItem.item()) {
        case OrderItemRequest.Digital digital ->
          OrderItem.digital(
            order,
            position,
            digital.productId(),
            pricedItem.productName(),
            currentPackageId(digital.productId()),
            pricedItem.unitPriceInGrosz()
          );

        case OrderItemRequest.Sweatshirt sweatshirt ->
          OrderItem.sweatshirt(
            order,
            position,
            sweatshirt.productId(),
            pricedItem.productName(),
            sweatshirt.patternId(),
            pricedItem.patternName(),
            sweatshirt.configuration().fit(),
            sweatshirt.configuration().size(),
            sweatshirt.configuration().color(),
            sweatshirt.configuration().embroideryOptionId(),
            sweatshirt.quantity(),
            pricedItem.unitPriceInGrosz()
          );
      };

      orderItems.add(orderItem);
    }

    orderItemRepository.saveAll(orderItems);

    return order;
  }

  private UUID currentPackageId(String productId) {
    return digitalPackages
      .findByProductIdAndCurrentPackageTrue(productId)
      .orElseThrow(() ->
        new OrderProductUnavailableException(productId)
      )
      .getId();
  }

  private String optionalText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    return value.trim();
  }
}
