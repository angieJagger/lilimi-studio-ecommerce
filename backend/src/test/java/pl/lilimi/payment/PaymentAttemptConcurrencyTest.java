package pl.lilimi.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.application.PaymentAttemptService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
class PaymentAttemptConcurrencyTest {

  @Autowired
  private PaymentAttemptService service;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private UUID orderId;

  @BeforeEach
  void createOrder() {
    var order = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        "payment-concurrency@example.com",
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

    jdbcTemplate.update(
      "DELETE FROM order_payment_attempts WHERE order_id = ?",
      orderId
    );

    jdbcTemplate.update(
      "DELETE FROM orders WHERE id = ?",
      orderId
    );
  }

  @Test
  void shouldReturnSameAttemptForConcurrentRequestsWithSameKey()
    throws Exception {

    UUID key = UUID.randomUUID();

    var outcomes = race(key, key);

    assertThat(outcomes).allSatisfy(outcome -> {
      assertThat(outcome.error()).isNull();
      assertThat(outcome.attemptId()).isNotNull();
    });

    assertThat(outcomes.get(0).attemptId())
      .isEqualTo(outcomes.get(1).attemptId());

    assertThat(attemptCount()).isEqualTo(1L);

    var storedKey = jdbcTemplate.queryForObject("""
        SELECT idempotency_key
        FROM order_payment_attempts
        WHERE order_id = ?
        """,
      UUID.class,
      orderId
    );

    assertThat(storedKey).isEqualTo(key);
  }

  @Test
  void shouldRejectSecondActiveAttemptForConcurrentDifferentKeys()
    throws Exception {

    var outcomes = race(
      UUID.randomUUID(),
      UUID.randomUUID()
    );

    var successful = outcomes.stream()
      .filter(outcome -> outcome.error() == null)
      .toList();

    var rejected = outcomes.stream()
      .filter(outcome -> outcome.error() != null)
      .toList();

    assertThat(successful).hasSize(1);
    assertThat(successful.get(0).attemptId()).isNotNull();

    assertThat(rejected).hasSize(1);
    assertThat(rejected.get(0).error())
      .isEqualTo("PAYMENT_ALREADY_IN_PROGRESS");

    assertThat(attemptCount()).isEqualTo(1L);
  }

  private List<Outcome> race(
    UUID firstKey,
    UUID secondKey
  ) throws Exception {
    return race(firstKey, secondKey, false);
  }

  private List<Outcome> race(
    UUID firstKey,
    UUID secondKey,
    boolean resume
  ) throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);

    try {
      var first = executor.submit(
        () -> prepare(firstKey, ready, start, resume)
      );

      var second = executor.submit(
        () -> prepare(secondKey, ready, start, resume)
      );

      assertThat(
        ready.await(10, TimeUnit.SECONDS)
      ).isTrue();

      start.countDown();

      return List.of(
        first.get(30, TimeUnit.SECONDS),
        second.get(30, TimeUnit.SECONDS)
      );
    } finally {
      start.countDown();
      executor.shutdownNow();

      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
          "Payment preparation threads did not terminate"
        );
      }
    }
  }

  private Outcome prepare(
    UUID key,
    CountDownLatch ready,
    CountDownLatch start,
    boolean resume
  ) throws Exception {
    ready.countDown();

    if (!start.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException(
        "Start signal timed out"
      );
    }

    try {
      var attempt = resume
        ? service.prepareForResumption(orderId, key)
        : service.prepare(orderId, key);
      return new Outcome(attempt.getId(), null);
    } catch (ResponseStatusException exception) {
      if (!HttpStatus.CONFLICT.equals(
        exception.getStatusCode()
      )) {
        throw exception;
      }

      return new Outcome(null, exception.getReason());
    }
  }

  private long attemptCount() {
    return jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM order_payment_attempts
        WHERE order_id = ?
        """,
      Long.class,
      orderId
    );
  }

  private record Outcome(
    UUID attemptId,
    String error
  ) {
  }
  @Test
  void shouldShareNewAttemptBetweenConcurrentResumptions()
    throws Exception {

    var outcomes = race(
      UUID.randomUUID(),
      UUID.randomUUID(),
      true
    );

    assertThat(outcomes).allSatisfy(outcome -> {
      assertThat(outcome.error()).isNull();
      assertThat(outcome.attemptId()).isNotNull();
    });

    assertThat(outcomes.get(0).attemptId())
      .isEqualTo(outcomes.get(1).attemptId());

    assertThat(attemptCount()).isEqualTo(1L);
  }

  @Test
  void shouldReuseExistingAttemptForConcurrentResumptions()
    throws Exception {

    var original = service.prepare(
      orderId,
      UUID.randomUUID()
    );

    var outcomes = race(
      UUID.randomUUID(),
      UUID.randomUUID(),
      true
    );

    assertThat(outcomes).allSatisfy(outcome -> {
      assertThat(outcome.error()).isNull();
      assertThat(outcome.attemptId())
        .isEqualTo(original.getId());
    });

    assertThat(attemptCount()).isEqualTo(1L);
  }
}
