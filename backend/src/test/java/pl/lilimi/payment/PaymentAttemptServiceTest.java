package pl.lilimi.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.application.PaymentAttemptService;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptServiceTest {

  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final UUID KEY = UUID.randomUUID();

  private static final Instant NOW =
    Instant.parse("2026-10-16T10:00:00Z");

  private static final List<PaymentAttemptStatus> ACTIVE_STATUSES =
    List.of(
      PaymentAttemptStatus.CREATED,
      PaymentAttemptStatus.PENDING
    );

  @Mock
  private OrderRepository orders;

  @Mock
  private PaymentAttemptRepository payments;

  @Mock
  private Order order;

  private PaymentAttemptService service;

  @BeforeEach
  void setUp() {
    service = new PaymentAttemptService(
      orders,
      payments,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void shouldCreateTestAttemptUsingSavedOrderAmounts() {
    existingOrder();

    when(order.getStatus()).thenReturn(OrderStatus.NEW);
    when(order.getTotalInGrosz()).thenReturn(54800L);
    when(order.getCurrency()).thenReturn("PLN");

    when(payments.saveAndFlush(any(PaymentAttempt.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    var attempt = service.prepare(ORDER_ID, KEY);

    assertThat(attempt.getOrderId()).isEqualTo(ORDER_ID);
    assertThat(attempt.getIdempotencyKey()).isEqualTo(KEY);
    assertThat(attempt.getAmountInGrosz()).isEqualTo(54800);
    assertThat(attempt.getCurrency()).isEqualTo("PLN");
    assertThat(attempt.isLivemode()).isFalse();
    assertThat(attempt.getCreatedAt()).isEqualTo(NOW);
    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);

    verify(orders).findByIdForUpdate(ORDER_ID);
    verify(payments).saveAndFlush(attempt);
  }

  @Test
  void shouldReturnExistingAttemptForSameKey() {
    existingOrder();

    var existing = new PaymentAttempt(
      ORDER_ID,
      KEY,
      4100,
      "PLN",
      false,
      NOW
    );

    when(payments.findByIdempotencyKey(KEY))
      .thenReturn(Optional.of(existing));

    var result = service.prepare(ORDER_ID, KEY);

    assertThat(result).isSameAs(existing);

    verify(payments, never()).saveAndFlush(any());
  }

  @Test
  void shouldRejectKeyAlreadyUsedForAnotherOrder() {
    existingOrder();

    var existing = new PaymentAttempt(
      UUID.randomUUID(),
      KEY,
      4100,
      "PLN",
      false,
      NOW
    );

    when(payments.findByIdempotencyKey(KEY))
      .thenReturn(Optional.of(existing));

    assertConflict("PAYMENT_IDEMPOTENCY_CONFLICT");

    verify(payments, never()).saveAndFlush(any());
  }

  @Test
  void shouldRejectAnotherKeyWhilePaymentIsActive() {
    existingOrder();

    when(order.getStatus()).thenReturn(OrderStatus.NEW);

    var active = new PaymentAttempt(
      ORDER_ID,
      UUID.randomUUID(),
      4100,
      "PLN",
      false,
      NOW
    );

    when(payments.findByOrderIdAndStatusIn(
      ORDER_ID,
      ACTIVE_STATUSES
    )).thenReturn(Optional.of(active));

    assertConflict("PAYMENT_ALREADY_IN_PROGRESS");

    verify(payments, never()).saveAndFlush(any());
  }

  @Test
  void shouldRejectNewAttemptForPaidOrder() {
    existingOrder();

    when(order.getStatus()).thenReturn(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    assertConflict("ORDER_ALREADY_PAID");

    verify(payments, never()).saveAndFlush(any());
  }

  @ParameterizedTest
  @EnumSource(
    value = OrderStatus.class,
    names = {"PROCESSING", "COMPLETED", "CANCELLED"}
  )
  void shouldRejectNewAttemptForNonPayableOrder(
    OrderStatus status
  ) {
    existingOrder();

    when(order.getStatus()).thenReturn(status);

    assertConflict("ORDER_NOT_PAYABLE");

    verify(payments, never()).saveAndFlush(any());
  }

  @Test
  void shouldRejectMissingOrder() {
    when(orders.findByIdForUpdate(ORDER_ID))
      .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.prepare(ORDER_ID, KEY))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(exception.getReason())
            .isEqualTo("ORDER_NOT_FOUND");
        }
      );

    verifyNoInteractions(payments);
  }

  private void existingOrder() {
    when(orders.findByIdForUpdate(ORDER_ID))
      .thenReturn(Optional.of(order));
  }

  private void assertConflict(String reason) {
    assertThatThrownBy(() -> service.prepare(ORDER_ID, KEY))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.getReason())
            .isEqualTo(reason);
        }
      );
  }
}
