package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OrderStatusConcurrencyTest {

  @Autowired
  private OrderRepository orders;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  void shouldAllowOnlyOneConcurrentStatusUpdate() throws Exception {
    var transaction = new TransactionTemplate(transactionManager);

    UUID id = transaction.execute(status ->
      orders.saveAndFlush(
        new Order(
          "pl",
          new OrderContact(
            "Anna Kowalska",
            "concurrency@example.com",
            null
          ),
          OrderDelivery.digital(),
          2900,
          0
        )
      ).getId()
    );

    assertThat(id).isNotNull();

    var bothLoaded = new CountDownLatch(2);
    var allowUpdates = new CountDownLatch(1);
    var successes = new AtomicInteger();
    var conflicts = new AtomicInteger();

    try {
      try (var executor = Executors.newFixedThreadPool(2)) {
        var first = executor.submit(() ->
          updateStatus(
            id,
            OrderStatus.PROCESSING,
            bothLoaded,
            allowUpdates,
            successes,
            conflicts
          )
        );

        var second = executor.submit(() ->
          updateStatus(
            id,
            OrderStatus.CANCELLED,
            bothLoaded,
            allowUpdates,
            successes,
            conflicts
          )
        );

        try {
          assertThat(bothLoaded.await(15, TimeUnit.SECONDS)).isTrue();

          allowUpdates.countDown();

          first.get(30, TimeUnit.SECONDS);
          second.get(30, TimeUnit.SECONDS);
        } finally {
          allowUpdates.countDown();
          executor.shutdownNow();
        }
      }

      assertThat(successes.get()).isEqualTo(1);
      assertThat(conflicts.get()).isEqualTo(1);

      var saved = transaction.execute(status ->
        orders.findById(id).orElseThrow()
      );

      assertThat(saved).isNotNull();
      assertThat(saved.getVersion()).isEqualTo(1);
      assertThat(saved.getStatus()).isIn(
        OrderStatus.PROCESSING,
        OrderStatus.CANCELLED
      );
    } finally {
      transaction.executeWithoutResult(status ->
        orders.deleteById(id)
      );
    }
  }

  private void updateStatus(
    UUID id,
    OrderStatus nextStatus,
    CountDownLatch bothLoaded,
    CountDownLatch allowUpdates,
    AtomicInteger successes,
    AtomicInteger conflicts
  ) {
    var transaction = new TransactionTemplate(transactionManager);

    try {
      transaction.executeWithoutResult(status -> {
        var order = orders.findById(id).orElseThrow();

        bothLoaded.countDown();
        awaitUpdates(allowUpdates);

        order.changeStatus(nextStatus);
        orders.flush();
      });

      successes.incrementAndGet();
    } catch (OptimisticLockingFailureException exception) {
      conflicts.incrementAndGet();
    }
  }

  private void awaitUpdates(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
          "Timed out waiting for concurrent updates"
        );
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();

      throw new IllegalStateException(
        "Concurrent update was interrupted",
        exception
      );
    }
  }
}
