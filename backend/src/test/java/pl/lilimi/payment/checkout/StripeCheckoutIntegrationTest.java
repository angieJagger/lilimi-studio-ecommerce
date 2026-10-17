package pl.lilimi.payment.checkout;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.service.checkout.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=true",
  "lilimi.payments.stripe.api-key=sk_test_placeholder_for_mocked_tests",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
class StripeCheckoutIntegrationTest {

  @Autowired
  private StripeCheckoutService checkout;

  @Autowired
  private PaymentAttemptRepository attempts;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private OrderItemRepository items;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @MockitoBean
  private SessionService sessions;

  private UUID orderId;

  @BeforeEach
  void createOrder() {
    var order = orders.saveAndFlush(
      new Order(
        "en",
        new OrderContact(
          "Anna Kowalska",
          "customer@example.com",
          null
        ),
        OrderDelivery.digital(),
        2900,
        0
      )
    );

    orderId = order.getId();

    items.saveAndFlush(
      OrderItem.digital(
        order,
        1,
        "pattern-001",
        "Saved pattern",
        2900
      )
    );
  }

  @AfterEach
  void cleanUp() {
    if (orderId == null) {
      return;
    }

    jdbc.update(
      "DELETE FROM order_payment_attempts WHERE order_id = ?",
      orderId
    );
    jdbc.update(
      "DELETE FROM order_items WHERE order_id = ?",
      orderId
    );
    jdbc.update(
      "DELETE FROM orders WHERE id = ?",
      orderId
    );
  }

  @Test
  void shouldCommitAttemptBeforeCallingStripeAndReuseSession()
    throws StripeException {

    var key = UUID.randomUUID();
    var createdSession = new AtomicReference<Session>();

    when(sessions.create(
      any(SessionCreateParams.class),
      any(RequestOptions.class)
    )).thenAnswer(invocation -> {
      SessionCreateParams params = invocation.getArgument(0);
      RequestOptions options = invocation.getArgument(1);

      assertCommittedAttempt(params, options);

      var session = openSession(params);
      createdSession.set(session);
      return session;
    });

    when(sessions.retrieve("cs_test_integration"))
      .thenAnswer(invocation -> {
        assertThat(
          TransactionSynchronizationManager
            .isActualTransactionActive()
        ).isFalse();

        return createdSession.get();
      });

    var first = checkout.start(orderId, key);
    var second = checkout.start(orderId, key);

    assertThat(first.paymentAttemptId())
      .isEqualTo(second.paymentAttemptId());
    assertThat(first.status()).isEqualTo("pending");
    assertThat(second.status()).isEqualTo("pending");
    assertThat(first.checkoutUrl())
      .isEqualTo("https://checkout.stripe.com/c/pay/test");
    assertThat(second.checkoutUrl())
      .isEqualTo(first.checkoutUrl());

    var saved = attempts.findById(first.paymentAttemptId())
      .orElseThrow();

    assertThat(saved.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
    assertThat(saved.getStripeCheckoutSessionId())
      .isEqualTo("cs_test_integration");

    assertThat(attemptCount()).isEqualTo(1L);

    verify(sessions, times(1)).create(
      any(SessionCreateParams.class),
      any(RequestOptions.class)
    );
    verify(sessions, times(1))
      .retrieve("cs_test_integration");
  }

  @Test
  void shouldKeepAttemptAfterStripeFailureAndReuseItOnRetry()
    throws StripeException {

    var key = UUID.randomUUID();
    var failure = mock(StripeException.class);

    when(sessions.create(
      any(SessionCreateParams.class),
      any(RequestOptions.class)
    ))
      .thenThrow(failure)
      .thenAnswer(invocation -> {
        SessionCreateParams params = invocation.getArgument(0);
        RequestOptions options = invocation.getArgument(1);

        assertCommittedAttempt(params, options);
        return openSession(params);
      });

    assertThatThrownBy(() -> checkout.start(orderId, key))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode().value())
            .isEqualTo(503);
          assertThat(exception.getReason())
            .isEqualTo("PAYMENT_PROVIDER_UNAVAILABLE");
        }
      );

    var afterFailure = attempts.findByIdempotencyKey(key)
      .orElseThrow();

    assertThat(afterFailure.getStatus())
      .isEqualTo(PaymentAttemptStatus.CREATED);
    assertThat(afterFailure.getStripeCheckoutSessionId())
      .isNull();

    var result = checkout.start(orderId, key);

    assertThat(result.paymentAttemptId())
      .isEqualTo(afterFailure.getId());
    assertThat(result.status()).isEqualTo("pending");

    var afterRetry = attempts.findById(afterFailure.getId())
      .orElseThrow();

    assertThat(afterRetry.getStatus())
      .isEqualTo(PaymentAttemptStatus.PENDING);
    assertThat(attemptCount()).isEqualTo(1L);

    verify(sessions, times(2)).create(
      any(SessionCreateParams.class),
      any(RequestOptions.class)
    );
    verify(sessions, never()).retrieve(any(String.class));
  }

  @Test
  void shouldRejectStartingCheckoutInsideTransaction() {
    var transaction = new TransactionTemplate(
      transactionManager
    );

    assertThatThrownBy(() ->
      transaction.executeWithoutResult(status ->
        checkout.start(orderId, UUID.randomUUID())
      )
    ).isInstanceOf(IllegalTransactionStateException.class);

    assertThat(attemptCount()).isZero();
    verifyNoInteractions(sessions);
  }

  private void assertCommittedAttempt(
    SessionCreateParams params,
    RequestOptions options
  ) {
    assertThat(
      TransactionSynchronizationManager
        .isActualTransactionActive()
    ).isFalse();

    var attemptId = UUID.fromString(
      params.getClientReferenceId()
    );

    var status = jdbc.queryForObject(
      """
      SELECT status
      FROM order_payment_attempts
      WHERE id = ? AND order_id = ?
      """,
      String.class,
      attemptId,
      orderId
    );

    assertThat(status).isEqualTo("created");
    assertThat(options.getIdempotencyKey())
      .isEqualTo("lilimi-checkout-" + attemptId);
  }

  private static Session openSession(
    SessionCreateParams params
  ) {
    var session = new Session();

    session.setId("cs_test_integration");
    session.setLivemode(false);
    session.setMode("payment");
    session.setStatus("open");
    session.setPaymentStatus("unpaid");
    session.setClientReferenceId(
      params.getClientReferenceId()
    );
    session.setMetadata(params.getMetadata());
    session.setAmountTotal(2900L);
    session.setCurrency("pln");
    session.setExpiresAt(
      Instant.now().plusSeconds(1800).getEpochSecond()
    );
    session.setUrl(
      "https://checkout.stripe.com/c/pay/test"
    );

    return session;
  }

  private long attemptCount() {
    return jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM order_payment_attempts
      WHERE order_id = ?
      """,
      Long.class,
      orderId
    );
  }

  @Test
  void shouldResumeSavedStripeSessionWithDifferentRequestKey()
    throws StripeException {

    var createdSession = new AtomicReference<Session>();

    when(sessions.create(
      any(SessionCreateParams.class),
      any(RequestOptions.class)
    )).thenAnswer(invocation -> {
      SessionCreateParams params = invocation.getArgument(0);
      RequestOptions options = invocation.getArgument(1);

      assertCommittedAttempt(params, options);

      var session = openSession(params);
      createdSession.set(session);
      return session;
    });

    when(sessions.retrieve("cs_test_integration"))
      .thenAnswer(invocation -> {
        assertThat(
          TransactionSynchronizationManager
            .isActualTransactionActive()
        ).isFalse();

        return createdSession.get();
      });

    var first = checkout.start(
      orderId,
      UUID.randomUUID()
    );

    var resumed = checkout.resume(
      orderId,
      UUID.randomUUID()
    );

    assertThat(resumed.paymentAttemptId())
      .isEqualTo(first.paymentAttemptId());
    assertThat(resumed.status()).isEqualTo("pending");
    assertThat(resumed.checkoutUrl())
      .isEqualTo(first.checkoutUrl());
    assertThat(attemptCount()).isEqualTo(1L);

    verify(sessions, times(1)).create(
      any(SessionCreateParams.class),
      any(RequestOptions.class)
    );
    verify(sessions, times(1))
      .retrieve("cs_test_integration");
  }
}
