package pl.lilimi.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTransitionTest {

  private Order newOrder() {
    return new Order(
      "pl",
      new OrderContact("Anna Kowalska", "anna@example.com", null),
      OrderDelivery.digital(),
      2900,
      0
    );
  }

  private Order processingOrder() {
    var order = newOrder();
    order.changeStatus(OrderStatus.PROCESSING);
    return order;
  }

  @Test
  void shouldMoveNewOrderToProcessing() {
    var order = newOrder();

    order.changeStatus(OrderStatus.PROCESSING);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
  }

  @Test
  void shouldCancelNewOrder() {
    var order = newOrder();

    order.changeStatus(OrderStatus.CANCELLED);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void shouldCompleteProcessingOrder() {
    var order = processingOrder();

    order.changeStatus(OrderStatus.COMPLETED);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
  }

  @Test
  void shouldCancelProcessingOrder() {
    var order = processingOrder();

    order.changeStatus(OrderStatus.CANCELLED);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void shouldRejectCompletingNewOrderDirectly() {
    var order = newOrder();

    assertThatThrownBy(() ->
      order.changeStatus(OrderStatus.COMPLETED)
    ).isInstanceOf(IllegalStateException.class);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
  }

  @Test
  void shouldRejectReturningProcessingOrderToNew() {
    var order = processingOrder();

    assertThatThrownBy(() ->
      order.changeStatus(OrderStatus.NEW)
    ).isInstanceOf(IllegalStateException.class);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
  }

  @Test
  void shouldRejectChangingCompletedOrderToAnotherStatus() {
    var order = processingOrder();
    order.changeStatus(OrderStatus.COMPLETED);

    for (var next : OrderStatus.values()) {
      if (next != OrderStatus.COMPLETED) {
        assertThatThrownBy(() -> order.changeStatus(next))
          .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
      }
    }
  }

  @Test
  void shouldRejectChangingCancelledOrderToAnotherStatus() {
    var order = newOrder();
    order.changeStatus(OrderStatus.CANCELLED);

    for (var next : OrderStatus.values()) {
      if (next != OrderStatus.CANCELLED) {
        assertThatThrownBy(() -> order.changeStatus(next))
          .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      }
    }
  }

  @Test
  void shouldAllowSettingTheSameStatusForEveryState() {
    var newOrder = newOrder();
    var processing = processingOrder();
    var completed = processingOrder();
    completed.changeStatus(OrderStatus.COMPLETED);
    var cancelled = newOrder();
    cancelled.changeStatus(OrderStatus.CANCELLED);

    for (var order : new Order[] {
      newOrder, processing, completed, cancelled
    }) {
      var originalStatus = order.getStatus();

      order.changeStatus(originalStatus);

      assertThat(order.getStatus()).isEqualTo(originalStatus);
    }
  }

  @Test
  void shouldRejectMissingStatus() {
    var order = newOrder();

    assertThatThrownBy(() -> order.changeStatus(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Order status is required");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
  }
}
