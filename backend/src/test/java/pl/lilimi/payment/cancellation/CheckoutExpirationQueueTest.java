package pl.lilimi.payment.cancellation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.admin.ChangeOrderStatusRequest;
import pl.lilimi.order.application.admin.AdminOrderStatusService;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
class CheckoutExpirationQueueTest {

  @Autowired
  private CheckoutExpirationQueue queue;

  @Autowired
  private AdminOrderStatusService orderStatus;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private PaymentAttemptRepository payments;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private UUID orderId;

  @BeforeEach
  void createOrder() {
    var order = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        "cancellation@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    ));

    orderId = order.getId();
  }

  @AfterEach
  void cleanUp() {
    if (orderId == null) {
      return;
    }

    jdbc.update(
      """
      DELETE FROM checkout_expiration_tasks
      WHERE payment_attempt_id IN (
        SELECT id
        FROM order_payment_attempts
        WHERE order_id = ?
      )
      """,
      orderId
    );

    jdbc.update(
      "DELETE FROM order_payment_attempts WHERE order_id = ?",
      orderId
    );

    jdbc.update(
      "DELETE FROM orders WHERE id = ?",
      orderId
    );
  }

  @Test
  void shouldQueueCreatedAttemptWhenOrderIsCancelled() {
    var attempt = saveAttempt(false);

    cancelOrder();

    assertThat(savedOrder().getStatus())
      .isEqualTo(OrderStatus.CANCELLED);
    assertThat(taskCount()).isEqualTo(1L);

    assertThat(jdbc.queryForObject(
      """
      SELECT status
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attempt.getId()
    )).isEqualTo("pending");
  }

  @Test
  void shouldQueuePendingAttemptOnlyOnce() {
    saveAttempt(true);

    cancelOrder();

    // Setting the same status again must not duplicate the task.
    cancelOrder();

    assertThat(taskCount()).isEqualTo(1L);
    assertThat(savedOrder().getStatus())
      .isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void shouldCancelOrderWithoutPaymentWithoutCreatingTask() {
    cancelOrder();

    assertThat(savedOrder().getStatus())
      .isEqualTo(OrderStatus.CANCELLED);
    assertThat(taskCount()).isZero();
  }

  @Test
  void shouldRollBackCancellationAndQueuedTaskTogether() {
    saveAttempt(true);

    var transaction = new TransactionTemplate(
      transactionManager
    );

    transaction.executeWithoutResult(status -> {
      cancelOrder();

      assertThat(taskCount()).isEqualTo(1L);

      status.setRollbackOnly();
    });

    assertThat(savedOrder().getStatus())
      .isEqualTo(OrderStatus.NEW);
    assertThat(savedOrder().getVersion()).isZero();
    assertThat(taskCount()).isZero();
  }

  @Test
  void shouldRequireTransactionWhenEnqueuing() {
    saveAttempt(true);

    assertThatThrownBy(() ->
      queue.enqueueForOrder(orderId)
    ).isInstanceOf(IllegalTransactionStateException.class);

    assertThat(taskCount()).isZero();
  }

  @Test
  void shouldReserveTaskWithoutOfferingItAgainDuringLease() {
    var attempt = saveAttempt(true);
    cancelOrder();

    Instant now = taskTime();

    var claim = queue.claimNext(now).orElseThrow();

    assertThat(claim.paymentAttemptId())
      .isEqualTo(attempt.getId());
    assertThat(claim.attemptNumber()).isEqualTo(1);
    assertThat(claim.leaseToken()).isNotNull();

    assertThat(queue.claimNext(now.plusSeconds(1)))
      .isEmpty();

    assertThat(queue.complete(
      claim,
      now.plusSeconds(2)
    )).isTrue();

    assertThat(queue.claimNext(now.plusSeconds(3)))
      .isEmpty();
  }

  @Test
  void shouldRecoverExpiredLeaseAndRejectPreviousOwner() {
    saveAttempt(true);
    cancelOrder();

    Instant now = taskTime();

    var original = queue.claimNext(now).orElseThrow();

    Instant afterLease = now.plusSeconds(301);

    var recovered = queue.claimNext(afterLease)
      .orElseThrow();

    assertThat(recovered.paymentAttemptId())
      .isEqualTo(original.paymentAttemptId());
    assertThat(recovered.leaseToken())
      .isNotEqualTo(original.leaseToken());
    assertThat(recovered.attemptNumber()).isEqualTo(2);

    assertThat(queue.complete(original, afterLease))
      .isFalse();

    assertThat(queue.retry(
      original,
      afterLease,
      "STRIPE_UNAVAILABLE"
    )).isFalse();

    assertThat(queue.complete(
      recovered,
      afterLease.plusSeconds(1)
    )).isTrue();
  }

  @Test
  void shouldDelayRetryAndReleasePreviousLease() {
    saveAttempt(true);
    cancelOrder();

    Instant now = taskTime();

    var original = queue.claimNext(now).orElseThrow();

    assertThat(queue.retry(
      original,
      now.plusSeconds(1),
      "STRIPE_UNAVAILABLE"
    )).isTrue();

    // First retry: ten seconds after the failure.
    assertThat(queue.claimNext(now.plusSeconds(10)))
      .isEmpty();

    var retried = queue.claimNext(now.plusSeconds(11))
      .orElseThrow();

    assertThat(retried.paymentAttemptId())
      .isEqualTo(original.paymentAttemptId());
    assertThat(retried.leaseToken())
      .isNotEqualTo(original.leaseToken());
    assertThat(retried.attemptNumber()).isEqualTo(2);

    assertThat(queue.complete(
      original,
      now.plusSeconds(12)
    )).isFalse();
  }

  @Test
  void shouldRequireReviewAfterFiveFailedAttempts() {
    var attempt = saveAttempt(true);
    cancelOrder();

    Instant now = taskTime();

    for (int number = 1; number <= 5; number++) {
      var claim = queue.claimNext(now).orElseThrow();

      assertThat(claim.attemptNumber()).isEqualTo(number);

      assertThat(queue.retry(
        claim,
        now.plusSeconds(1),
        "STRIPE_UNAVAILABLE"
      )).isTrue();

      now = now.plusSeconds(3600);
    }

    assertThat(queue.claimNext(now)).isEmpty();

    assertThat(jdbc.queryForObject(
      """
      SELECT status
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attempt.getId()
    )).isEqualTo("needs_review");

    assertThat(jdbc.queryForObject(
      """
      SELECT attempts
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      Integer.class,
      attempt.getId()
    )).isEqualTo(5);
  }

  @Test
  void shouldRequireReviewAfterRepeatedWorkerCrashes() {
    var attempt = saveAttempt(true);
    cancelOrder();

    Instant now = taskTime();

    for (int number = 1; number <= 5; number++) {
      var claim = queue.claimNext(now).orElseThrow();

      assertThat(claim.attemptNumber()).isEqualTo(number);

      // Simulate a worker that disappears without finishing.
      now = now.plusSeconds(301);
    }

    assertThat(queue.claimNext(now)).isEmpty();

    assertThat(jdbc.queryForObject(
      """
      SELECT status
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attempt.getId()
    )).isEqualTo("needs_review");

    assertThat(jdbc.queryForObject(
      """
      SELECT last_error_code
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attempt.getId()
    )).isEqualTo("EXPIRATION_ATTEMPTS_EXHAUSTED");
  }

  @Test
  void shouldAllowImmediateReviewWithoutFurtherRetries() {
    var attempt = saveAttempt(true);
    cancelOrder();

    Instant now = taskTime();

    var claim = queue.claimNext(now).orElseThrow();

    assertThat(queue.needsReview(
      claim,
      now.plusSeconds(1),
      "PAYMENT_ALREADY_COMPLETED"
    )).isTrue();

    assertThat(queue.claimNext(now.plusSeconds(3600)))
      .isEmpty();

    assertThat(jdbc.queryForObject(
      """
      SELECT status
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attempt.getId()
    )).isEqualTo("needs_review");

    assertThat(jdbc.queryForObject(
      """
      SELECT last_error_code
      FROM checkout_expiration_tasks
      WHERE payment_attempt_id = ?
      """,
      String.class,
      attempt.getId()
    )).isEqualTo("PAYMENT_ALREADY_COMPLETED");
  }

  private static Instant taskTime() {
    return Instant.now()
      .plusSeconds(1)
      .truncatedTo(ChronoUnit.MICROS);
  }

  private void cancelOrder() {
    var order = savedOrder();

    orderStatus.changeStatus(
      orderId,
      new ChangeOrderStatusRequest(
        "cancelled",
        order.getVersion()
      )
    );
  }

  private Order savedOrder() {
    return orders.findById(orderId).orElseThrow();
  }

  private PaymentAttempt saveAttempt(boolean attachSession) {
    Instant createdAt = Instant.now().minusSeconds(60);

    var attempt = new PaymentAttempt(
      orderId,
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      createdAt
    );

    if (attachSession) {
      attempt.attachCheckoutSession(
        "cs_test_" + UUID.randomUUID(),
        createdAt.plusSeconds(1800)
      );
    }

    return payments.saveAndFlush(attempt);
  }

  private long taskCount() {
    return jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM checkout_expiration_tasks task
      JOIN order_payment_attempts attempt
        ON attempt.id = task.payment_attempt_id
      WHERE attempt.order_id = ?
      """,
      Long.class,
      orderId
    );
  }
}
