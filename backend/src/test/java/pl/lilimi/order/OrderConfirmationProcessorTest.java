package pl.lilimi.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mail.MailSendException;
import pl.lilimi.order.application.OrderConfirmationMailService;
import pl.lilimi.order.application.OrderConfirmationMessage;
import pl.lilimi.order.application.OrderConfirmationMessageFactory;
import pl.lilimi.order.application.OrderConfirmationProcessor;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.persistence.OrderConfirmationEmailRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderConfirmationProcessorTest {

  private static final Instant NOW =
    Instant.parse("2026-10-16T08:00:00Z");

  private static final UUID ORDER_ID =
    UUID.fromString("d71ef77e-7d74-4f69-a8e3-177fe55b99f0");

  @Mock
  private OrderConfirmationEmailRepository queue;

  @Mock
  private OrderRepository orders;

  @Mock
  private OrderItemRepository items;

  @Mock
  private OrderConfirmationMessageFactory messageFactory;

  @Mock
  private OrderConfirmationMailService mailService;

  private OrderConfirmationProcessor processor;

  private final OrderConfirmationMessage message =
    new OrderConfirmationMessage(
      "customer@example.com",
      "Order received",
      "Test confirmation"
    );

  @BeforeEach
  void setUp() {
    processor = new OrderConfirmationProcessor(
      queue,
      orders,
      items,
      messageFactory,
      mailService,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void shouldReturnFalseWhenNoEmailIsDue() {
    when(queue.lockNext(NOW)).thenReturn(Optional.empty());

    assertThat(processor.processNext()).isFalse();

    verifyNoInteractions(
      orders,
      items,
      messageFactory,
      mailService
    );
  }

  @Test
  void shouldSendMessageAndMarkItAsSent() {
    preparePendingEmail(0);

    assertThat(processor.processNext()).isTrue();

    var inOrder = org.mockito.Mockito.inOrder(
      mailService,
      queue
    );

    inOrder.verify(mailService).send(message);
    inOrder.verify(queue).markSent(ORDER_ID, NOW);

    verify(queue, never()).recordFailure(
      any(),
      org.mockito.ArgumentMatchers.anyBoolean(),
      any(),
      any()
    );
  }

  @ParameterizedTest
  @CsvSource({
    "0, 60",
    "1, 300",
    "2, 900",
    "3, 3600"
  })
  void shouldScheduleRetryAfterSmtpFailure(
    int previousAttempts,
    long delayInSeconds
  ) {
    preparePendingEmail(previousAttempts);

    doThrow(new MailSendException("Simulated SMTP failure"))
      .when(mailService)
      .send(message);

    assertThat(processor.processNext()).isTrue();

    verify(queue).recordFailure(
      ORDER_ID,
      true,
      NOW.plusSeconds(delayInSeconds),
      "SMTP_SEND_FAILED"
    );

    verify(queue, never()).markSent(any(), any());
  }

  @Test
  void shouldStopRetryingAfterFifthFailedAttempt() {
    preparePendingEmail(4);

    doThrow(new MailSendException("Simulated SMTP failure"))
      .when(mailService)
      .send(message);

    assertThat(processor.processNext()).isTrue();

    verify(queue).recordFailure(
      ORDER_ID,
      false,
      NOW,
      "SMTP_SEND_FAILED"
    );

    verify(queue, never()).markSent(any(), any());
  }

  @Test
  void shouldRejectInvalidMessageWithoutSendingIt() {
    var order = mock(Order.class);
    var orderItems = List.of(mock(OrderItem.class));

    when(queue.lockNext(NOW)).thenReturn(
      Optional.of(
        new OrderConfirmationEmailRepository.PendingEmail(
          ORDER_ID,
          0
        )
      )
    );

    when(orders.findById(ORDER_ID))
      .thenReturn(Optional.of(order));

    when(items.findByOrder_IdOrderByPositionAsc(ORDER_ID))
      .thenReturn(orderItems);

    when(messageFactory.create(order, orderItems))
      .thenThrow(new IllegalArgumentException(
        "Simulated invalid message"
      ));

    assertThat(processor.processNext()).isTrue();

    verify(queue).recordFailure(
      ORDER_ID,
      false,
      NOW,
      "MESSAGE_INVALID"
    );

    verifyNoInteractions(mailService);
    verify(queue, never()).markSent(any(), any());
  }

  @Test
  void shouldNotTreatDatabaseFailureAsSmtpFailure() {
    when(queue.lockNext(NOW))
      .thenThrow(new DataAccessResourceFailureException(
        "Simulated database failure"
      ));

    assertThatThrownBy(() -> processor.processNext())
      .isInstanceOf(DataAccessResourceFailureException.class);

    verifyNoInteractions(mailService);

    verify(queue, never()).recordFailure(
      any(),
      org.mockito.ArgumentMatchers.anyBoolean(),
      any(),
      any()
    );
  }

  private void preparePendingEmail(int previousAttempts) {
    var order = mock(Order.class);
    var orderItems = List.of(mock(OrderItem.class));

    when(queue.lockNext(NOW)).thenReturn(
      Optional.of(
        new OrderConfirmationEmailRepository.PendingEmail(
          ORDER_ID,
          previousAttempts
        )
      )
    );

    when(orders.findById(ORDER_ID))
      .thenReturn(Optional.of(order));

    when(items.findByOrder_IdOrderByPositionAsc(ORDER_ID))
      .thenReturn(orderItems);

    when(messageFactory.create(order, orderItems))
      .thenReturn(message);
  }
}
