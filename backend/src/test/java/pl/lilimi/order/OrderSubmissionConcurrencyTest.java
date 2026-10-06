package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderContactRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.application.OrderSubmissionService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OrderSubmissionConcurrencyTest {

  @Autowired
  private OrderSubmissionService submissionService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateOnlyOneOrderForConcurrentSubmissions()
    throws Exception {

    var key = UUID.randomUUID();

    var request = new CreateOrderRequest(
      "pl",
      new OrderContactRequest(
        "Test Customer",
        "customer@example.com",
        null
      ),
      new OrderDeliveryRequest.Digital(),
      List.of(
        new OrderItemRequest.Digital("pattern-001", 1)
      )
    );

    long ordersBefore = count("orders");
    long itemsBefore = count("order_items");

    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);

    Callable<UUID> submit = () -> {
      ready.countDown();

      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Start signal timed out");
      }

      return submissionService.submit(key, request).getId();
    };

    try {
      var first = executor.submit(submit);
      var second = executor.submit(submit);

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      var firstId = first.get(30, TimeUnit.SECONDS);
      var secondId = second.get(30, TimeUnit.SECONDS);

      assertThat(secondId).isEqualTo(firstId);
      assertThat(count("orders")).isEqualTo(ordersBefore + 1);
      assertThat(count("order_items")).isEqualTo(itemsBefore + 1);

      var storedOrderId = jdbcTemplate.queryForObject("""
                SELECT order_id
                FROM order_submissions
                WHERE idempotency_key = ?
                """,
        UUID.class,
        key
      );

      assertThat(storedOrderId).isEqualTo(firstId);
    } finally {
      start.countDown();
      executor.shutdownNow();

      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
          "Submission threads did not terminate"
        );
      }

      cleanUp(key);
    }
  }

  private long count(String table) {
    return switch (table) {
      case "orders" -> jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM orders",
        Long.class
      );
      case "order_items" -> jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM order_items",
        Long.class
      );
      default -> throw new IllegalArgumentException(
        "Unsupported table"
      );
    };
  }

  private void cleanUp(UUID key) {
    var orderIds = jdbcTemplate.queryForList("""
            SELECT order_id
            FROM order_submissions
            WHERE idempotency_key = ?
              AND order_id IS NOT NULL
            """,
      UUID.class,
      key
    );

    jdbcTemplate.update("""
            DELETE FROM order_submissions
            WHERE idempotency_key = ?
            """,
      key
    );

    for (var orderId : orderIds) {
      jdbcTemplate.update(
        "DELETE FROM order_items WHERE order_id = ?",
        orderId
      );

      jdbcTemplate.update(
        "DELETE FROM orders WHERE id = ?",
        orderId
      );
    }
  }
}
