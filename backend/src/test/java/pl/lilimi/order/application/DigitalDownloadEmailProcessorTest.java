package pl.lilimi.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.MailSendException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.DigitalDownloadEmailRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigitalDownloadEmailProcessorTest {

  private static final UUID ORDER_ID = UUID.randomUUID();

  private static final Instant NOW =
    Instant.parse("2026-11-01T10:00:00Z");

  @Mock
  private DigitalDownloadEmailRepository queue;

  @Mock
  private OrderRepository orders;

  @Mock
  private OrderItemRepository items;

  @Mock
  private PaymentAttemptRepository payments;

  @Mock
  private DigitalDownloadTokenIssuer tokenIssuer;

  @Mock
  private DigitalDownloadEmailMessageFactory messageFactory;

  @Mock
  private DigitalDownloadMailService mailService;

  @Mock
  private Order order;

  private DigitalDownloadEmailProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new DigitalDownloadEmailProcessor(
      queue,
      orders,
      items,
      payments,
      tokenIssuer,
      messageFactory,
      mailService,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void shouldDoNothingWhenQueueIsEmpty() {
    when(queue.lockNext(NOW)).thenReturn(Optional.empty());

    assertThat(processor.processNext()).isFalse();

    verifyNoInteractions(
      orders,
      items,
      payments,
      tokenIssuer,
      messageFactory,
      mailService
    );
  }

  @Test
  void shouldSendDownloadEmailAndMarkItSent() {
    prepareEntry(0);
    prepareEligibleOrder();

    var issued = new IssuedDigitalDownloadToken(
      ORDER_ID,
      "a".repeat(43),
      NOW.plusSeconds(604800)
    );

    var message = new DigitalDownloadEmailMessage(
      "customer@example.com",
      "Pliki do pobrania",
      "Treść z linkiem do pobrania."
    );

    when(tokenIssuer.issue(ORDER_ID)).thenReturn(issued);
    when(messageFactory.create(order, issued)).thenReturn(message);

    assertThat(processor.processNext()).isTrue();

    var sequence = inOrder(
      tokenIssuer,
      messageFactory,
      mailService,
      queue
    );

    sequence.verify(tokenIssuer).issue(ORDER_ID);
    sequence.verify(messageFactory).create(order, issued);
    sequence.verify(mailService).send(message);
    sequence.verify(queue).markSent(ORDER_ID, NOW);

    verify(queue, never()).recordFailure(
      any(),
      anyBoolean(),
      any(),
      anyString()
    );
  }

  @Test
  void shouldSkipCancelledOrderWithoutIssuingToken() {
    prepareEntry(0);

    when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);

    assertThat(processor.processNext()).isTrue();

    verify(queue).markSkipped(ORDER_ID, "ORDER_CANCELLED");

    verifyNoInteractions(
      payments,
      items,
      tokenIssuer,
      messageFactory,
      mailService
    );
  }

  @Test
  void shouldRetryAfterFirstSmtpFailure() {
    prepareEntry(0);
    prepareEligibleOrder();

    var message = prepareMessage();

    doThrow(new MailSendException("Simulated SMTP failure"))
      .when(mailService)
      .send(message);

    assertThat(processor.processNext()).isTrue();

    verify(queue).recordFailure(
      ORDER_ID,
      true,
      NOW.plusSeconds(60),
      "SMTP_SEND_FAILED"
    );

    verify(queue, never()).markSent(any(), any());
  }

  @Test
  void shouldStopRetryingAfterFifthSmtpFailure() {
    prepareEntry(4);
    prepareEligibleOrder();

    var message = prepareMessage();

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
  void shouldSkipOrderWithoutConfirmedPayment() {
    prepareEntry(0);

    when(order.getStatus()).thenReturn(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(false);

    assertThat(processor.processNext()).isTrue();

    verify(queue).markSkipped(ORDER_ID, "ORDER_NOT_PAID");

    verifyNoInteractions(
      items,
      tokenIssuer,
      messageFactory,
      mailService
    );
  }

  @Test
  void shouldSkipOrderWithoutDigitalPackage() {
    prepareEntry(0);

    when(order.getStatus()).thenReturn(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    when(items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      ORDER_ID,
      "digital"
    )).thenReturn(false);

    assertThat(processor.processNext()).isTrue();

    verify(queue).markSkipped(
      ORDER_ID,
      "DIGITAL_DOWNLOAD_NOT_AVAILABLE"
    );

    verifyNoInteractions(tokenIssuer, messageFactory, mailService);
  }

  private void prepareEntry(int attempts) {
    when(queue.lockNext(NOW)).thenReturn(Optional.of(
      new DigitalDownloadEmailRepository.PendingEmail(
        ORDER_ID,
        attempts
      )
    ));

    when(orders.findByIdForUpdate(ORDER_ID))
      .thenReturn(Optional.of(order));
  }

  private void prepareEligibleOrder() {
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

  private DigitalDownloadEmailMessage prepareMessage() {
    var issued = new IssuedDigitalDownloadToken(
      ORDER_ID,
      "a".repeat(43),
      NOW.plusSeconds(604800)
    );

    var message = new DigitalDownloadEmailMessage(
      "customer@example.com",
      "Pliki do pobrania",
      "Treść z linkiem do pobrania."
    );

    when(tokenIssuer.issue(ORDER_ID)).thenReturn(issued);
    when(messageFactory.create(order, issued)).thenReturn(message);

    return message;
  }
}
