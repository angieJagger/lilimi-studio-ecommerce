package pl.lilimi.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_payment_attempts")
public class PaymentAttempt {

  @Id
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(
    name = "idempotency_key",
    nullable = false,
    unique = true,
    updatable = false
  )
  private UUID idempotencyKey;

  @Column(
    name = "provider",
    length = 16,
    nullable = false,
    updatable = false
  )
  private String provider;

  @Convert(converter = PaymentAttemptStatusConverter.class)
  @Column(name = "status", length = 16, nullable = false)
  private PaymentAttemptStatus status;

  @Column(
    name = "amount_in_grosz",
    nullable = false,
    updatable = false
  )
  private long amountInGrosz;

  @Column(
    name = "currency",
    length = 3,
    nullable = false,
    updatable = false
  )
  private String currency;

  @Column(
    name = "stripe_checkout_session_id",
    length = 255,
    unique = true
  )
  private String stripeCheckoutSessionId;

  @Column(
    name = "stripe_payment_intent_id",
    length = 255,
    unique = true
  )
  private String stripePaymentIntentId;

  @Column(name = "livemode", nullable = false, updatable = false)
  private boolean livemode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected PaymentAttempt() {
    // Required by JPA.
  }

  public PaymentAttempt(
    UUID orderId,
    UUID idempotencyKey,
    long amountInGrosz,
    String currency,
    boolean livemode,
    Instant createdAt
  ) {
    Objects.requireNonNull(orderId, "Order ID is required");
    Objects.requireNonNull(
      idempotencyKey,
      "Idempotency key is required"
    );
    Objects.requireNonNull(createdAt, "Creation time is required");

    if (amountInGrosz <= 0) {
      throw new IllegalArgumentException(
        "Payment amount must be positive"
      );
    }

    String normalizedCurrency = normalizeCurrency(currency);

    if (!"PLN".equals(normalizedCurrency)) {
      throw new IllegalArgumentException(
        "Only PLN payments are supported"
      );
    }

    this.id = UUID.randomUUID();
    this.orderId = orderId;
    this.idempotencyKey = idempotencyKey;
    this.provider = "stripe";
    this.status = PaymentAttemptStatus.CREATED;
    this.amountInGrosz = amountInGrosz;
    this.currency = normalizedCurrency;
    this.livemode = livemode;
    this.createdAt = createdAt;
  }

  public void attachCheckoutSession(
    String sessionId,
    Instant expiresAt
  ) {
    requireStripeId(sessionId, "cs_");
    Objects.requireNonNull(
      expiresAt,
      "Session expiration is required"
    );

    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException(
        "Session must expire after attempt creation"
      );
    }

    if (
      status == PaymentAttemptStatus.PENDING
        && sessionId.equals(stripeCheckoutSessionId)
        && expiresAt.equals(this.expiresAt)
    ) {
      return;
    }

    if (status != PaymentAttemptStatus.CREATED) {
      throw new IllegalStateException(
        "Checkout session cannot be attached in this state"
      );
    }

    this.stripeCheckoutSessionId = sessionId;
    this.expiresAt = expiresAt;
    this.status = PaymentAttemptStatus.PENDING;
  }

  public void markCreationFailed() {
    if (status == PaymentAttemptStatus.FAILED) {
      return;
    }

    if (status != PaymentAttemptStatus.CREATED) {
      throw new IllegalStateException(
        "Only an uninitialized attempt can fail creation"
      );
    }

    this.status = PaymentAttemptStatus.FAILED;
  }

  public void markExpired(String sessionId) {
    requireMatchingSession(sessionId);

    if (
      status == PaymentAttemptStatus.EXPIRED
        || status == PaymentAttemptStatus.SUCCEEDED
        || status == PaymentAttemptStatus.FAILED
    ) {
      return;
    }

    if (status != PaymentAttemptStatus.PENDING) {
      throw new IllegalStateException(
        "Only a pending session can expire"
      );
    }

    this.status = PaymentAttemptStatus.EXPIRED;
  }

  public void markFailed(String sessionId) {
    requireMatchingSession(sessionId);

    if (
      status == PaymentAttemptStatus.FAILED
        || status == PaymentAttemptStatus.SUCCEEDED
    ) {
      return;
    }

    if (
      status != PaymentAttemptStatus.PENDING
        && status != PaymentAttemptStatus.EXPIRED
    ) {
      throw new IllegalStateException(
        "Session cannot fail in this state"
      );
    }

    this.status = PaymentAttemptStatus.FAILED;
  }

  public void confirmPayment(
    String sessionId,
    String paymentIntentId,
    long receivedAmountInGrosz,
    String receivedCurrency,
    boolean receivedLivemode,
    Instant paidAt
  ) {
    requireMatchingSession(sessionId);
    requireStripeId(paymentIntentId, "pi_");
    Objects.requireNonNull(paidAt, "Payment time is required");

    if (
      receivedAmountInGrosz != amountInGrosz
        || !currency.equals(normalizeCurrency(receivedCurrency))
        || receivedLivemode != livemode
    ) {
      throw new IllegalArgumentException(
        "Payment details do not match the attempt"
      );
    }

    if (status == PaymentAttemptStatus.SUCCEEDED) {
      if (!paymentIntentId.equals(stripePaymentIntentId)) {
        throw new IllegalStateException(
          "A different payment was already confirmed"
        );
      }

      return;
    }

    this.stripePaymentIntentId = paymentIntentId;
    this.paidAt = paidAt;
    this.status = PaymentAttemptStatus.SUCCEEDED;
  }

  private void requireMatchingSession(String sessionId) {
    requireStripeId(sessionId, "cs_");

    if (!sessionId.equals(stripeCheckoutSessionId)) {
      throw new IllegalArgumentException(
        "Checkout session does not match the attempt"
      );
    }
  }

  private static void requireStripeId(
    String value,
    String prefix
  ) {
    if (
      value == null
        || !value.startsWith(prefix)
        || value.length() <= prefix.length()
        || value.length() > 255
        || !value.equals(value.strip())
    ) {
      throw new IllegalArgumentException(
        "Invalid Stripe identifier"
      );
    }
  }

  private static String normalizeCurrency(String value) {
    Objects.requireNonNull(value, "Currency is required");
    return value.strip().toUpperCase(Locale.ROOT);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getIdempotencyKey() {
    return idempotencyKey;
  }

  public PaymentAttemptStatus getStatus() {
    return status;
  }

  public long getAmountInGrosz() {
    return amountInGrosz;
  }

  public String getCurrency() {
    return currency;
  }

  public String getStripeCheckoutSessionId() {
    return stripeCheckoutSessionId;
  }

  public String getStripePaymentIntentId() {
    return stripePaymentIntentId;
  }

  public boolean isLivemode() {
    return livemode;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public long getVersion() {
    return version;
  }
}
