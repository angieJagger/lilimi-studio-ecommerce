package pl.lilimi.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.order.domain.DigitalDownloadToken;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.DigitalDownloadTokenRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigitalDownloadTokenIssuerTest {

  private static final UUID ORDER_ID = UUID.randomUUID();

  private static final Instant NOW =
    Instant.parse("2026-11-01T10:00:00Z");

  private static final Duration VALIDITY = Duration.ofDays(7);

  @Mock
  private OrderRepository orders;

  @Mock
  private OrderItemRepository items;

  @Mock
  private PaymentAttemptRepository payments;

  @Mock
  private DigitalDownloadTokenRepository tokens;

  @Mock
  private Order order;

  private final DigitalDownloadTokenCodec codec =
    new DigitalDownloadTokenCodec();

  private DigitalDownloadTokenIssuer issuer;

  @BeforeEach
  void setUp() {
    issuer = new DigitalDownloadTokenIssuer(
      orders,
      items,
      payments,
      tokens,
      codec,
      Clock.fixed(NOW, ZoneOffset.UTC),
      VALIDITY
    );
  }

  @Test
  void shouldIssueTokenForPaidOrderWithDigitalPackage() {
    prepareOrder(OrderStatus.NEW);
    preparePaidOrder();

    when(items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      ORDER_ID,
      "digital"
    )).thenReturn(true);

    var issued = issuer.issue(ORDER_ID);

    var captor = ArgumentCaptor.forClass(
      DigitalDownloadToken.class
    );

    verify(tokens).save(captor.capture());

    var saved = captor.getValue();

    assertThat(issued.orderId()).isEqualTo(ORDER_ID);
    assertThat(issued.rawToken())
      .matches("[A-Za-z0-9_-]{43}");
    assertThat(issued.expiresAt()).isEqualTo(NOW.plus(VALIDITY));

    assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
    assertThat(saved.getExpiresAt()).isEqualTo(issued.expiresAt());
    assertThat(saved.isUsableAt(NOW)).isTrue();

    assertThat(issued.toString())
      .doesNotContain(issued.rawToken())
      .contains("[REDACTED]");
  }

  @Test
  void shouldRejectUnknownOrder() {
    when(orders.findByIdForUpdate(ORDER_ID))
      .thenReturn(Optional.empty());

    assertRejected(404, "ORDER_NOT_FOUND");

    verifyNoInteractions(payments, items, tokens);
  }

  @Test
  void shouldRejectCancelledOrder() {
    prepareOrder(OrderStatus.CANCELLED);

    assertRejected(409, "ORDER_CANCELLED");

    verifyNoInteractions(payments, items, tokens);
  }

  @Test
  void shouldRejectUnpaidOrderEvenWhenCompleted() {
    prepareOrder(OrderStatus.COMPLETED);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(false);

    assertRejected(409, "ORDER_NOT_PAID");

    verifyNoInteractions(items, tokens);
  }

  @Test
  void shouldRejectOrderWithoutAssignedDigitalPackage() {
    prepareOrder(OrderStatus.NEW);
    preparePaidOrder();

    when(items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      ORDER_ID,
      "digital"
    )).thenReturn(false);

    assertRejected(409, "DIGITAL_DOWNLOAD_NOT_AVAILABLE");

    verifyNoInteractions(tokens);
  }

  private void prepareOrder(OrderStatus status) {
    when(orders.findByIdForUpdate(ORDER_ID))
      .thenReturn(Optional.of(order));

    when(order.getStatus()).thenReturn(status);
  }

  private void preparePaidOrder() {
    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);
  }

  private void assertRejected(int status, String reason) {
    assertThatThrownBy(() -> issuer.issue(ORDER_ID))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode().value())
            .isEqualTo(status);
          assertThat(exception.getReason()).isEqualTo(reason);
        }
      );
  }
}
