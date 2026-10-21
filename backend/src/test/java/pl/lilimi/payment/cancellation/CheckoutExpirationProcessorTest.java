package pl.lilimi.payment.cancellation;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.lilimi.payment.checkout.CheckoutSessionPersistenceService;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.stripe.StripeCheckoutGateway;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutExpirationProcessorTest {

  private static final Instant NOW =
    Instant.parse("2026-10-20T10:00:00Z");

  private static final Instant EXPIRES_AT =
    NOW.plusSeconds(1800);

  private static final String SESSION_ID =
    "cs_test_expiration";

  @Mock
  private CheckoutExpirationQueue queue;

  @Mock
  private CheckoutSessionPersistenceService persistence;

  @Mock
  private StripeCheckoutGateway gateway;

  private CheckoutExpirationProcessor processor;
  private PaymentAttempt attempt;
  private CheckoutExpirationQueue.Claim claim;

  @BeforeEach
  void setUp() {
    processor = new CheckoutExpirationProcessor(
      queue,
      persistence,
      gateway,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );

    attempt = new PaymentAttempt(
      UUID.randomUUID(),
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      NOW.minusSeconds(60)
    );

    claim = new CheckoutExpirationQueue.Claim(
      attempt.getId(),
      UUID.randomUUID(),
      1
    );
  }

  @Test
  void shouldReturnFalseWhenQueueIsEmpty() {
    when(queue.claimNext(NOW)).thenReturn(Optional.empty());

    assertThat(processor.processNext()).isFalse();

    verifyNoInteractions(persistence, gateway);
  }

  @Test
  void shouldExpireOpenSessionAndCompleteTask()
    throws StripeException {

    attachSession();
    prepareTask();
    stubRecording();

    when(gateway.retrieve(attempt))
      .thenReturn(session("open", "unpaid"));

    when(gateway.expire(attempt))
      .thenReturn(session("expired", "unpaid"));

    assertThat(processor.processNext()).isTrue();

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.EXPIRED);

    verify(gateway).expire(attempt);
    verify(queue).complete(claim, NOW);
    verify(queue, never()).retry(
      any(),
      any(),
      any()
    );
  }

  @Test
  void shouldCompleteTaskWhenStripeSessionIsAlreadyExpired()
    throws StripeException {

    attachSession();
    prepareTask();
    stubRecording();

    when(gateway.retrieve(attempt))
      .thenReturn(session("expired", "unpaid"));

    assertThat(processor.processNext()).isTrue();

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.EXPIRED);

    verify(gateway, never()).expire(any());
    verify(queue).complete(claim, NOW);
  }

  @Test
  void shouldRecoverCreatedSessionBeforeExpiringIt()
    throws StripeException {

    var params = prepareTask();
    stubRecording();

    when(gateway.create(attempt, params))
      .thenReturn(session("open", "unpaid"));

    when(gateway.expire(attempt))
      .thenReturn(session("expired", "unpaid"));

    assertThat(processor.processNext()).isTrue();

    assertThat(attempt.getStripeCheckoutSessionId())
      .isEqualTo(SESSION_ID);
    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.EXPIRED);

    verify(gateway).create(attempt, params);
    verify(gateway).expire(attempt);
    verify(gateway, never()).retrieve(any());
    verify(queue).complete(claim, NOW);
  }

  @ParameterizedTest
  @CsvSource({
    "paid, PAYMENT_ALREADY_COMPLETED",
    "unpaid, PAYMENT_PROCESSING"
  })
  void shouldRequireReviewForCompletedCheckout(
    String paymentStatus,
    String expectedReason
  ) throws StripeException {

    attachSession();
    prepareTask();
    stubRecording();

    when(gateway.retrieve(attempt))
      .thenReturn(session("complete", paymentStatus));

    assertThat(processor.processNext()).isTrue();

    verify(gateway, never()).expire(any());
    verify(queue).needsReview(
      claim,
      NOW,
      expectedReason
    );
    verify(queue, never()).complete(any(), any());

    // Only a verified webhook confirms payment.
    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
  }

  @Test
  void shouldRetryProviderFailureWithoutChangingPayment()
    throws StripeException {

    attachSession();
    prepareTask();

    var failure = mock(StripeException.class);

    when(gateway.retrieve(attempt))
      .thenThrow(failure);

    assertThat(processor.processNext()).isTrue();

    verify(queue).retry(
      claim,
      NOW,
      "STRIPE_UNAVAILABLE"
    );
    verify(queue, never()).complete(any(), any());
    verify(gateway, never()).expire(any());

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
  }

  @Test
  void shouldRequireReviewForAlreadyConfirmedPayment() {
    attachSession();
    confirmPayment();
    prepareTask();

    assertThat(processor.processNext()).isTrue();

    verify(queue).needsReview(
      claim,
      NOW,
      "PAYMENT_ALREADY_COMPLETED"
    );
    verify(queue, never()).complete(any(), any());
    verifyNoInteractions(gateway);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
  }

  @Test
  void shouldPreservePaymentConfirmedDuringExpiration()
    throws StripeException {

    attachSession();
    prepareTask();
    stubRecording();

    when(gateway.retrieve(attempt))
      .thenReturn(session("open", "unpaid"));

    when(gateway.expire(attempt))
      .thenAnswer(invocation -> {
        // Simulate a concurrent webhook updating the payment.
        confirmPayment();
        return session("expired", "unpaid");
      });

    assertThat(processor.processNext()).isTrue();

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(attempt.getStripePaymentIntentId())
      .isEqualTo("pi_test_confirmed");

    verify(queue).needsReview(
      claim,
      NOW,
      "PAYMENT_ALREADY_COMPLETED"
    );
    verify(queue, never()).complete(any(), any());
  }

  private void attachSession() {
    attempt.attachCheckoutSession(
      SESSION_ID,
      EXPIRES_AT
    );
  }

  private void confirmPayment() {
    attempt.confirmPayment(
      SESSION_ID,
      "pi_test_confirmed",
      2900,
      "PLN",
      false,
      NOW
    );
  }

  private SessionCreateParams prepareTask() {
    SessionCreateParams params =
      attempt.getStatus() == PaymentAttemptStatus.CREATED
        ? SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.PAYMENT)
        .setClientReferenceId(attempt.getId().toString())
        .build()
        : null;

    when(queue.claimNext(NOW))
      .thenReturn(Optional.of(claim));

    when(persistence.loadForCancellation(attempt.getId()))
      .thenReturn(
        new CheckoutSessionPersistenceService.PreparedCheckout(
          attempt,
          params
        )
      );

    return params;
  }

  private void stubRecording() {
    when(persistence.recordSession(
      eq(attempt.getOrderId()),
      eq(attempt.getId()),
      eq(SESSION_ID),
      eq(EXPIRES_AT),
      anyBoolean()
    )).thenAnswer(invocation -> {
      if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
        attempt.attachCheckoutSession(
          SESSION_ID,
          EXPIRES_AT
        );
      }

      boolean expired = invocation.getArgument(4);

      if (expired) {
        attempt.markExpired(SESSION_ID);
      }

      return attempt;
    });
  }

  private Session session(
    String status,
    String paymentStatus
  ) {
    var session = new Session();

    session.setId(SESSION_ID);
    session.setStatus(status);
    session.setPaymentStatus(paymentStatus);
    session.setExpiresAt(EXPIRES_AT.getEpochSecond());

    return session;
  }
}
