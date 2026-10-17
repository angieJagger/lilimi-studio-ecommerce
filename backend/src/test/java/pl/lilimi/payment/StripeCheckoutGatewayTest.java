package pl.lilimi.payment;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.service.checkout.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.infrastructure.StripeCheckoutGateway;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutGatewayTest {

  private static final Instant NOW =
    Instant.parse("2026-10-16T10:00:00Z");

  private static final String SESSION_ID =
    "cs_test_example";

  @Mock
  private SessionService sessions;

  private StripeCheckoutGateway gateway;
  private PaymentAttempt attempt;

  @BeforeEach
  void setUp() {
    gateway = new StripeCheckoutGateway(
      sessions,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );

    attempt = new PaymentAttempt(
      UUID.randomUUID(),
      UUID.randomUUID(),
      4100,
      "PLN",
      false,
      NOW
    );
  }

  @Test
  void shouldCreateSessionWithAttemptIdempotencyKey()
    throws StripeException {

    var params = params(attempt);
    var session = session(attempt);

    when(sessions.create(
      eq(params),
      any(RequestOptions.class)
    )).thenReturn(session);

    var result = gateway.create(attempt, params);

    assertThat(result).isSameAs(session);

    var options = ArgumentCaptor.forClass(
      RequestOptions.class
    );

    verify(sessions).create(eq(params), options.capture());

    assertThat(options.getValue().getIdempotencyKey())
      .isEqualTo("lilimi-checkout-" + attempt.getId());
  }

  @Test
  void shouldReuseSameKeyWhenCreatingSessionAgain()
    throws StripeException {

    var params = params(attempt);
    var session = session(attempt);

    when(sessions.create(
      eq(params),
      any(RequestOptions.class)
    )).thenReturn(session);

    gateway.create(attempt, params);
    gateway.create(attempt, params);

    var options = ArgumentCaptor.forClass(
      RequestOptions.class
    );

    verify(sessions, times(2))
      .create(eq(params), options.capture());

    assertThat(options.getAllValues())
      .extracting(RequestOptions::getIdempotencyKey)
      .containsExactly(
        "lilimi-checkout-" + attempt.getId(),
        "lilimi-checkout-" + attempt.getId()
      );
  }

  @Test
  void shouldPropagateStripeFailureWithoutFailingAttempt()
    throws StripeException {

    var params = params(attempt);
    var failure = mock(StripeException.class);

    when(sessions.create(
      eq(params),
      any(RequestOptions.class)
    )).thenThrow(failure);

    assertThatThrownBy(() ->
      gateway.create(attempt, params)
    ).isSameAs(failure);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);

    assertThat(attempt.getStripeCheckoutSessionId()).isNull();
  }

  @Test
  void shouldStopCreationAfterIdempotencyWindow() {
    var laterGateway = new StripeCheckoutGateway(
      sessions,
      Clock.fixed(
        NOW.plusSeconds(23 * 60 * 60),
        ZoneOffset.UTC
      )
    );

    assertThatThrownBy(() ->
      laterGateway.create(attempt, params(attempt))
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(
        "PAYMENT_SESSION_RECONCILIATION_REQUIRED"
      );

    verifyNoInteractions(sessions);
  }

  @Test
  void shouldRejectParametersForDifferentAttempt() {
    var incorrectParams = SessionCreateParams.builder()
      .setClientReferenceId(UUID.randomUUID().toString())
      .build();

    assertThatThrownBy(() ->
      gateway.create(attempt, incorrectParams)
    ).isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(sessions);
  }

  @Test
  void shouldRejectLiveAttemptBeforeCallingStripe() {
    var liveAttempt = new PaymentAttempt(
      UUID.randomUUID(),
      UUID.randomUUID(),
      4100,
      "PLN",
      true,
      NOW
    );

    assertThatThrownBy(() ->
      gateway.create(liveAttempt, params(liveAttempt))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Live payments are not enabled");

    verifyNoInteractions(sessions);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "amount",
    "currency",
    "mode",
    "attempt",
    "order",
    "livemode"
  })
  void shouldRejectMismatchedStripeResponse(String mismatch)
    throws StripeException {

    var params = params(attempt);
    var session = session(attempt);

    switch (mismatch) {
      case "amount" -> session.setAmountTotal(4000L);
      case "currency" -> session.setCurrency("eur");
      case "mode" -> session.setMode("subscription");
      case "attempt" -> session.setClientReferenceId(
        UUID.randomUUID().toString()
      );
      case "order" -> session.setMetadata(Map.of(
        "payment_attempt_id", attempt.getId().toString(),
        "order_id", UUID.randomUUID().toString()
      ));
      case "livemode" -> session.setLivemode(true);
      default -> throw new IllegalArgumentException(
        "Unknown test case"
      );
    }

    when(sessions.create(
      eq(params),
      any(RequestOptions.class)
    )).thenReturn(session);

    assertThatThrownBy(() ->
      gateway.create(attempt, params)
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("STRIPE_SESSION_MISMATCH");

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
  }

  @Test
  void shouldRetrieveSavedSession() throws StripeException {
    attempt.attachCheckoutSession(
      SESSION_ID,
      NOW.plusSeconds(1800)
    );

    var session = session(attempt);

    when(sessions.retrieve(SESSION_ID))
      .thenReturn(session);

    assertThat(gateway.retrieve(attempt))
      .isSameAs(session);

    verify(sessions).retrieve(SESSION_ID);
  }

  @Test
  void shouldRejectRetrievedSessionWithDifferentId()
    throws StripeException {

    attempt.attachCheckoutSession(
      SESSION_ID,
      NOW.plusSeconds(1800)
    );

    var session = session(attempt);
    session.setId("cs_test_other");

    when(sessions.retrieve(SESSION_ID))
      .thenReturn(session);

    assertThatThrownBy(() -> gateway.retrieve(attempt))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("STRIPE_SESSION_MISMATCH");
  }

  private SessionCreateParams params(
    PaymentAttempt payment
  ) {
    return SessionCreateParams.builder()
      .setMode(SessionCreateParams.Mode.PAYMENT)
      .setClientReferenceId(payment.getId().toString())
      .putMetadata(
        "payment_attempt_id",
        payment.getId().toString()
      )
      .putMetadata(
        "order_id",
        payment.getOrderId().toString()
      )
      .build();
  }

  private Session session(PaymentAttempt payment) {
    var session = new Session();

    session.setId(SESSION_ID);
    session.setLivemode(false);
    session.setMode("payment");
    session.setStatus("open");
    session.setPaymentStatus("unpaid");
    session.setClientReferenceId(payment.getId().toString());
    session.setAmountTotal(payment.getAmountInGrosz());
    session.setCurrency("pln");
    session.setExpiresAt(NOW.plusSeconds(1800).getEpochSecond());
    session.setUrl(
      "https://checkout.stripe.com/c/pay/test-example"
    );

    session.setMetadata(Map.of(
      "payment_attempt_id", payment.getId().toString(),
      "order_id", payment.getOrderId().toString()
    ));

    return session;
  }
}
