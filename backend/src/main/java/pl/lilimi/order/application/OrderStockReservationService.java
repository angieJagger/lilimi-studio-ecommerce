package pl.lilimi.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.catalog.GarmentVariantRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.cancellation.CheckoutExpirationQueue;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OrderStockReservationService {

  private static final List<PaymentAttemptStatus> STOCK_RELEASE_BLOCKING_STATUSES =
    List.of(PaymentAttemptStatus.CREATED, PaymentAttemptStatus.PENDING, PaymentAttemptStatus.SUCCEEDED);

  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final ProductRepository products;
  private final GarmentVariantRepository variants;
  private final PaymentAttemptRepository payments;
  private final CheckoutExpirationQueue expirationQueue;

  public OrderStockReservationService(
    OrderRepository orders,
    OrderItemRepository items,
    ProductRepository products,
    GarmentVariantRepository variants,
    PaymentAttemptRepository payments,
    CheckoutExpirationQueue expirationQueue
  ) {
    this.orders = orders;
    this.items = items;
    this.products = products;
    this.variants = variants;
    this.payments = payments;
    this.expirationQueue = expirationQueue;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void onCancellation(Order order) {
    expirationQueue.enqueueForOrder(order.getId());
    releaseStockIfSafe(order.getId());
  }

  @Transactional
  public void processDueOrder(UUID orderId, Instant cutoff) {
    // Use the same order lock as checkout, admin status changes and webhooks.
    var order = orders.findByIdForUpdate(orderId).orElse(null);
    if (order == null) {
      return;
    }

    if (order.getStatus() == OrderStatus.CANCELLED) {
      releaseStockIfSafe(orderId);
    } else if (order.getStatus() == OrderStatus.NEW && !order.getCreatedAt().isAfter(cutoff)
      && !payments.existsByOrderIdAndStatus(orderId, PaymentAttemptStatus.SUCCEEDED)) {
      order.requestReservationExpiration();
      expirationQueue.enqueueForOrder(orderId);
      if (!payments.existsByOrderIdAndStatusIn(orderId, STOCK_RELEASE_BLOCKING_STATUSES)) {
        order.changeStatus(OrderStatus.CANCELLED);
        releaseStockIfSafe(orderId);
      }
    }
  }

  private void releaseStockIfSafe(UUID orderId) {
    if (payments.existsByOrderIdAndStatusIn(orderId, STOCK_RELEASE_BLOCKING_STATUSES)) {
      return;
    }

    var reserved = items.findByOrder_IdOrderByPositionAsc(orderId).stream()
      .filter(OrderItem::isStockReserved)
      .sorted(Comparator.comparing(OrderItem::getProductId))
      .toList();

    for (var item : reserved) {
      var product = products.findByIdForUpdate(item.getProductId()).orElseThrow();
      if (item.getReservedVariantId() == null) {
        product.changeStockQuantity(Math.addExact(product.getStockQuantity(), item.getQuantity()));
      } else {
        var variant = variants.findByIdAndProductId(item.getReservedVariantId(), product.getId()).orElseThrow();
        variant.changeStockQuantity(Math.addExact(variant.getStockQuantity(), item.getQuantity()));
      }
      item.markStockReleased();
    }
  }
}
