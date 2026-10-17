package pl.lilimi.payment.checkout;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.stripe.StripeCheckoutGateway;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutServiceTest {

  private static final Instant NOW =
    Instant.parse("2026-10-16T10:00:00Z");

  private static final Instant EXPIRES_AT =
    NOW.plusSeconds(1800);

  private static final String SESSION_ID =
    "cs_test_example";

  private static final String CHECKOUT_URL =
    "https://checkout.stripe.com/c/pay/test-example";

  @Mock
  private PaymentAttemptService attempts;

  @Mock
  private CheckoutSessionPersistenceService persistence;

  @Mock
  private StripeCheckoutGateway gateway;

  private StripeCheckoutService service;
  private PaymentAttempt attempt;
  private SessionCreateParams params;

  @BeforeEach
  void setUp() {
    service = new StripeCheckoutService(
      attempts,
      persistence,
      gateway
    );

    attempt = new PaymentAttempt(
      UUID.randomUUID(),
      UUID.randomUUID(),
      4100,
      "PLN",
      false,
      NOW
    );

    params = SessionCreateParams.builder()
      .setMode(SessionCreateParams.Mode.PAYMENT)
      .build();
  }

  @Test
  void shouldCreateAndSaveSessionBeforeReturningCheckoutUrl()
    throws StripeException {

    prepare();
    recordCreatedSession();

    when(gateway.create(attempt, params))
      .thenReturn(session());

    var result = start();

    assertThat(result.paymentAttemptId())
      .isEqualTo(attempt.getId());
    assertThat(result.status()).isEqualTo("pending");
    assertThat(result.checkoutUrl()).isEqualTo(CHECKOUT_URL);

    var inOrder = org.mockito.Mockito.inOrder(
      gateway,
      persistence
    );

    inOrder.verify(gateway).create(attempt, params);
    inOrder.verify(persistence).recordSession(
      attempt.getOrderId(),
      attempt.getId(),
      SESSION_ID,
      EXPIRES_AT,
      false
    );
    inOrder.verify(persistence).load(attempt.getId());
  }

  @Test
  void shouldRetrieveExistingSessionInsteadOfCreatingAnother()
    throws StripeException {

    attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
    prepare();

    when(gateway.retrieve(attempt)).thenReturn(session());

    var result = start();

    assertThat(result.checkoutUrl()).isEqualTo(CHECKOUT_URL);

    verify(gateway).retrieve(attempt);
    verify(gateway, never()).create(any(), any());
  }

  @Test
  void shouldLeaveAttemptUnresolvedAfterStripeFailure()
    throws StripeException {

    prepare();

    var failure = mock(StripeException.class);

    when(gateway.create(attempt, params))
      .thenThrow(failure);

    assertThatThrownBy(this::start)
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(exception.getReason())
            .isEqualTo("PAYMENT_PROVIDER_UNAVAILABLE");
        }
      );

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);

    verify(persistence, never()).recordSession(
      any(),
      any(),
      any(),
      any(),
      org.mockito.ArgumentMatchers.anyBoolean()
    );
  }

  @Test
  void shouldNotCallStripeForAlreadySuccessfulAttempt() {
    attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
    confirmPayment();
    prepare();

    var result = start();

    assertThat(result.status()).isEqualTo("succeeded");
    assertThat(result.checkoutUrl()).isNull();

    verifyNoInteractions(gateway);
  }

  @Test
  void shouldNotRedirectForCompletedSessionAwaitingConfirmation()
    throws StripeException {

    attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
    prepare();

    var session = session();
    session.setStatus("complete");
    session.setUrl(null);

    when(gateway.retrieve(attempt)).thenReturn(session);

    var result = start();

    assertThat(result.status()).isEqualTo("pending");
    assertThat(result.checkoutUrl()).isNull();
  }

  @Test
  void shouldRejectCheckoutUrlOutsideStripe()
    throws StripeException {

    attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
    prepare();

    var session = session();
    session.setUrl("https://example.com/payment");

    when(gateway.retrieve(attempt)).thenReturn(session);

    assertThatThrownBy(this::start)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("STRIPE_CHECKOUT_URL_INVALID");
  }

  @Test
  void shouldRequireReconciliationAfterCreationWindow()
    throws StripeException {

    prepare();

    when(gateway.create(attempt, params))
      .thenThrow(new IllegalStateException(
        "PAYMENT_SESSION_RECONCILIATION_REQUIRED"
      ));

    assertThatThrownBy(this::start)
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
          assertThat(exception.getReason())
            .isEqualTo(
              "PAYMENT_SESSION_RECONCILIATION_REQUIRED"
            );
        }
      );
  }

  @Test
  void shouldNotRedirectIfPaymentWasConfirmedDuringProcessing()
    throws StripeException {

    prepare();

    when(gateway.create(attempt, params))
      .thenReturn(session());

    doAnswer(invocation -> {
      attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
      confirmPayment();
      return attempt;
    }).when(persistence).recordSession(
      attempt.getOrderId(),
      attempt.getId(),
      SESSION_ID,
      EXPIRES_AT,
      false
    );

    var result = start();

    assertThat(result.status()).isEqualTo("succeeded");
    assertThat(result.checkoutUrl()).isNull();
  }

  private void prepare() {
    when(attempts.prepare(
      attempt.getOrderId(),
      attempt.getIdempotencyKey()
    )).thenReturn(attempt);

    when(persistence.load(attempt.getId()))
      .thenReturn(
        new CheckoutSessionPersistenceService.PreparedCheckout(
          attempt,
          params
        )
      );
  }

  private void recordCreatedSession() {
    doAnswer(invocation -> {
      attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
      return attempt;
    }).when(persistence).recordSession(
      attempt.getOrderId(),
      attempt.getId(),
      SESSION_ID,
      EXPIRES_AT,
      false
    );
  }

  private void confirmPayment() {
    attempt.confirmPayment(
      SESSION_ID,
      "pi_example",
      4100,
      "PLN",
      false,
      NOW.plusSeconds(60)
    );
  }

  private pl.lilimi.payment.checkout.CheckoutSessionResult start() {
    return service.start(
      attempt.getOrderId(),
      attempt.getIdempotencyKey()
    );
  }

  private Session session() {
    var session = new Session();

    session.setId(SESSION_ID);
    session.setStatus("open");
    session.setPaymentStatus("unpaid");
    session.setExpiresAt(EXPIRES_AT.getEpochSecond());
    session.setUrl(CHECKOUT_URL);

    return session;
  }
}
