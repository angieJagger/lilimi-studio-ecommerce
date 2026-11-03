package pl.lilimi.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.DigitalDownloadEmailRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendDigitalDownloadLinkServiceTest {

  private static final UUID ORDER_ID = UUID.randomUUID();

  private static final Instant NOW =
    Instant.parse("2026-11-02T10:00:00Z");

  private static final Duration COOLDOWN = Duration.ofMinutes(5);

  @Mock
  private OrderRepository orders;

  @Mock
  private OrderItemRepository items;

  @Mock
  private PaymentAttemptRepository payments;

  @Mock
  private DigitalDownloadEmailRepository queue;

  @Mock
  private Order order;

  private ResendDigitalDownloadLinkService service;

  @BeforeEach
  void setUp() {
    service = new ResendDigitalDownloadLinkService(
      orders,
      items,
      payments,
      queue,
      Clock.fixed(NOW, ZoneOffset.UTC),
      COOLDOWN
    );
  }

  @Test
  void shouldQueueResendForMatchingPaidOrder() {
    prepareMatchingOrder();
    preparePaidOrderWithPackage();

    service.request(ORDER_ID, " CUSTOMER@example.com ");

    verify(queue).enqueueAgain(ORDER_ID, NOW, COOLDOWN);
  }

  @Test
  void shouldIgnoreUnknownOrder() {
    when(orders.findById(ORDER_ID))
      .thenReturn(Optional.empty());

    service.request(ORDER_ID, "customer@example.com");

    verifyNoInteractions(payments, items, queue);
  }

  @Test
  void shouldIgnoreDifferentEmail() {
    prepareMatchingOrder();

    service.request(ORDER_ID, "other@example.com");

    verifyNoInteractions(payments, items, queue);
  }

  @Test
  void shouldIgnoreCancelledOrder() {
    prepareMatchingOrder();
    when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);

    service.request(ORDER_ID, "customer@example.com");

    verifyNoInteractions(payments, items, queue);
  }

  @Test
  void shouldIgnoreUnpaidOrder() {
    prepareMatchingOrder();

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(false);

    service.request(ORDER_ID, "customer@example.com");

    verifyNoInteractions(items, queue);
  }

  @Test
  void shouldIgnoreOrderWithoutAssignedDigitalPackage() {
    prepareMatchingOrder();

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    when(items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      ORDER_ID,
      "digital"
    )).thenReturn(false);

    service.request(ORDER_ID, "customer@example.com");

    verifyNoInteractions(queue);
  }

  @Test
  void shouldIgnoreMissingInputWithoutReadingDatabase() {
    service.request(null, "customer@example.com");
    service.request(ORDER_ID, null);
    service.request(ORDER_ID, " ");

    verifyNoInteractions(orders, payments, items, queue);
  }

  private void prepareMatchingOrder() {
    when(orders.findById(ORDER_ID))
      .thenReturn(Optional.of(order));

    when(order.getContact()).thenReturn(new OrderContact(
      "Test Customer",
      "customer@example.com",
      null
    ));
  }

  private void preparePaidOrderWithPackage() {
    when(order.getStatus()).thenReturn(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    when(items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      ORDER_ID,
      "digital"
    )).thenReturn(true);
  }
}
