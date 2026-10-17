package pl.lilimi.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAttemptTest {

  private static final Instant CREATED_AT =
    Instant.parse("2026-10-16T10:00:00Z");

  private static final Instant EXPIRES_AT =
    CREATED_AT.plusSeconds(1800);

  private static final Instant PAID_AT =
    CREATED_AT.plusSeconds(60);

  private static final String SESSION_ID = "cs_test_example";
  private static final String INTENT_ID = "pi_example";

  @Test
  void shouldCreateUninitializedAttempt() {
    UUID orderId = UUID.randomUUID();
    UUID key = UUID.randomUUID();

    var attempt = new PaymentAttempt(
      orderId,
      key,
      4100,
      "pln",
      false,
      CREATED_AT
    );

    assertThat(attempt.getId()).isNotNull();
    assertThat(attempt.getOrderId()).isEqualTo(orderId);
    assertThat(attempt.getIdempotencyKey()).isEqualTo(key);
    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(attempt.getAmountInGrosz()).isEqualTo(4100);
    assertThat(attempt.getCurrency()).isEqualTo("PLN");
    assertThat(attempt.isLivemode()).isFalse();
    assertThat(attempt.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(attempt.getStripeCheckoutSessionId()).isNull();
    assertThat(attempt.getPaidAt()).isNull();
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void shouldRejectNonPositiveAmount(long amount) {
    assertThatThrownBy(() -> new PaymentAttempt(
      UUID.randomUUID(),
      UUID.randomUUID(),
      amount,
      "PLN",
      false,
      CREATED_AT
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectUnsupportedCurrency() {
    assertThatThrownBy(() -> new PaymentAttempt(
      UUID.randomUUID(),
      UUID.randomUUID(),
      4100,
      "EUR",
      false,
      CREATED_AT
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAttachSessionIdempotently() {
    var attempt = newAttempt();

    attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
    attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
    assertThat(attempt.getStripeCheckoutSessionId())
      .isEqualTo(SESSION_ID);
    assertThat(attempt.getExpiresAt()).isEqualTo(EXPIRES_AT);
  }

  @Test
  void shouldRejectReplacingAnAttachedSession() {
    var attempt = pendingAttempt();

    assertThatThrownBy(() ->
      attempt.attachCheckoutSession(
        "cs_test_other",
        EXPIRES_AT
      )
    ).isInstanceOf(IllegalStateException.class);

    assertThat(attempt.getStripeCheckoutSessionId())
      .isEqualTo(SESSION_ID);
  }

  @Test
  void shouldRejectInvalidSessionExpiration() {
    var attempt = newAttempt();

    assertThatThrownBy(() ->
      attempt.attachCheckoutSession(
        SESSION_ID,
        CREATED_AT
      )
    ).isInstanceOf(IllegalArgumentException.class);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(attempt.getStripeCheckoutSessionId()).isNull();
  }

  @Test
  void shouldMarkDefinitiveCreationFailure() {
    var attempt = newAttempt();

    attempt.markCreationFailed();
    attempt.markCreationFailed();

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.FAILED);

    assertThatThrownBy(() ->
      attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT)
    ).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectCreationFailureAfterSessionWasAttached() {
    var attempt = pendingAttempt();

    assertThatThrownBy(attempt::markCreationFailed)
      .isInstanceOf(IllegalStateException.class);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
  }

  @Test
  void shouldConfirmMatchingPayment() {
    var attempt = pendingAttempt();

    confirm(attempt);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(attempt.getStripePaymentIntentId())
      .isEqualTo(INTENT_ID);
    assertThat(attempt.getPaidAt()).isEqualTo(PAID_AT);
  }

  @Test
  void shouldRejectMismatchedSession() {
    var attempt = pendingAttempt();

    assertThatThrownBy(() -> attempt.confirmPayment(
      "cs_test_other",
      INTENT_ID,
      4100,
      "PLN",
      false,
      PAID_AT
    )).isInstanceOf(IllegalArgumentException.class);

    assertUnpaid(attempt);
  }

  @Test
  void shouldRejectMismatchedAmount() {
    var attempt = pendingAttempt();

    assertThatThrownBy(() -> attempt.confirmPayment(
      SESSION_ID,
      INTENT_ID,
      4000,
      "PLN",
      false,
      PAID_AT
    )).isInstanceOf(IllegalArgumentException.class);

    assertUnpaid(attempt);
  }

  @Test
  void shouldRejectMismatchedCurrency() {
    var attempt = pendingAttempt();

    assertThatThrownBy(() -> attempt.confirmPayment(
      SESSION_ID,
      INTENT_ID,
      4100,
      "EUR",
      false,
      PAID_AT
    )).isInstanceOf(IllegalArgumentException.class);

    assertUnpaid(attempt);
  }

  @Test
  void shouldRejectMismatchedMode() {
    var attempt = pendingAttempt();

    assertThatThrownBy(() -> attempt.confirmPayment(
      SESSION_ID,
      INTENT_ID,
      4100,
      "PLN",
      true,
      PAID_AT
    )).isInstanceOf(IllegalArgumentException.class);

    assertUnpaid(attempt);
  }

  @Test
  void shouldPreserveOriginalPaymentTimeOnRepeatedConfirmation() {
    var attempt = pendingAttempt();
    confirm(attempt);

    attempt.confirmPayment(
      SESSION_ID,
      INTENT_ID,
      4100,
      "pln",
      false,
      PAID_AT.plusSeconds(120)
    );

    assertThat(attempt.getPaidAt()).isEqualTo(PAID_AT);
    assertThat(attempt.getStripePaymentIntentId())
      .isEqualTo(INTENT_ID);
  }

  @Test
  void shouldRejectDifferentPaymentIntentAfterSuccess() {
    var attempt = pendingAttempt();
    confirm(attempt);

    assertThatThrownBy(() -> attempt.confirmPayment(
      SESSION_ID,
      "pi_other",
      4100,
      "PLN",
      false,
      PAID_AT
    )).isInstanceOf(IllegalStateException.class);

    assertThat(attempt.getStripePaymentIntentId())
      .isEqualTo(INTENT_ID);
  }

  @Test
  void shouldNotDowngradeSuccessfulPayment() {
    var attempt = pendingAttempt();
    confirm(attempt);

    attempt.markExpired(SESSION_ID);
    attempt.markFailed(SESSION_ID);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(attempt.getPaidAt()).isEqualTo(PAID_AT);
  }

  @Test
  void shouldAcceptVerifiedPaymentAfterExpiration() {
    var attempt = pendingAttempt();

    attempt.markExpired(SESSION_ID);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.EXPIRED);

    confirm(attempt);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
  }

  @Test
  void shouldAcceptVerifiedPaymentAfterFailure() {
    var attempt = pendingAttempt();

    attempt.markFailed(SESSION_ID);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.FAILED);

    confirm(attempt);

    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
  }

  private PaymentAttempt newAttempt() {
    return new PaymentAttempt(
      UUID.randomUUID(),
      UUID.randomUUID(),
      4100,
      "PLN",
      false,
      CREATED_AT
    );
  }

  private PaymentAttempt pendingAttempt() {
    var attempt = newAttempt();
    attempt.attachCheckoutSession(SESSION_ID, EXPIRES_AT);
    return attempt;
  }

  private void confirm(PaymentAttempt attempt) {
    attempt.confirmPayment(
      SESSION_ID,
      INTENT_ID,
      4100,
      "PLN",
      false,
      PAID_AT
    );
  }

  private void assertUnpaid(PaymentAttempt attempt) {
    assertThat(attempt.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
    assertThat(attempt.getPaidAt()).isNull();
    assertThat(attempt.getStripePaymentIntentId()).isNull();
  }
}
