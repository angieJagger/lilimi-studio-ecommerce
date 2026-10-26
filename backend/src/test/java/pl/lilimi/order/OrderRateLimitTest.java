package pl.lilimi.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.api.OrderContactRequest;
import pl.lilimi.order.api.OrderDeliveryRequest;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.application.OrderProductUnavailableException;
import pl.lilimi.order.application.OrderRateLimitExceededException;
import pl.lilimi.order.application.OrderSubmissionService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
  "spring.application.name=order-rate-limit-test"
})
@Import(PostgresTestConfiguration.class)
class OrderRateLimitTest {

  private static final Instant NOW =
    Instant.parse("2026-10-26T12:00:00Z");

  private static final String SOURCE = "203.0.113.10";

  @Autowired
  private OrderSubmissionService submissions;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private Clock clock;

  @BeforeEach
  void resetTestDatabase() {
    jdbc.update("DELETE FROM order_submissions");
    jdbc.update("DELETE FROM order_items");
    jdbc.update("DELETE FROM orders");
    jdbc.update("DELETE FROM request_rate_limits");

    when(clock.instant()).thenReturn(NOW);
  }

  @Test
  void shouldAllowTenOrdersAndRejectEleventhWithoutSavingSubmission() {
    createOrders(10);

    UUID rejectedKey = UUID.randomUUID();

    assertThatThrownBy(() ->
      submissions.submitFromSource(
        rejectedKey,
        request(),
        null,
        SOURCE
      )
    )
      .isInstanceOf(OrderRateLimitExceededException.class)
      .satisfies(exception ->
        assertThat(
          ((OrderRateLimitExceededException) exception)
            .getRetryAfterSeconds()
        ).isEqualTo(900)
      );

    assertThat(orderCount()).isEqualTo(10);

    assertThat(jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM order_submissions
      WHERE idempotency_key = ?
      """,
      Long.class,
      rejectedKey
    )).isZero();
  }

  @Test
  void shouldReturnExistingOrderEvenWhenLimitIsExhausted() {
    UUID originalKey = UUID.randomUUID();

    var original = submissions.submitFromSource(
      originalKey,
      request(),
      null,
      SOURCE
    );

    createOrders(9);

    var repeated = submissions.submitFromSource(
      originalKey,
      request(),
      null,
      SOURCE
    );

    assertThat(repeated.getId()).isEqualTo(original.getId());
    assertThat(orderCount()).isEqualTo(10);

    assertThat(jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM order_confirmation_emails
      WHERE order_id = ?
      """,
      Long.class,
      original.getId()
    )).isEqualTo(1);
  }

  @Test
  void shouldAllowNewOrderWhenWindowExpires() {
    createOrders(10);

    when(clock.instant()).thenReturn(
      NOW.plusSeconds(900)
    );

    var order = submissions.submitFromSource(
      UUID.randomUUID(),
      request(),
      null,
      SOURCE
    );

    assertThat(order.getId()).isNotNull();
    assertThat(orderCount()).isEqualTo(11);
  }

  @Test
  void shouldNotConsumeBudgetWhenOrderCreationFails() {
    var unavailableRequest = new CreateOrderRequest(
      "en",
      new OrderContactRequest(
        "Test Customer",
        "order-limit@example.com",
        null
      ),
      new OrderDeliveryRequest.Digital(),
      List.of(
        new OrderItemRequest.Digital(
          "missing-test-product",
          1
        )
      )
    );

    assertThatThrownBy(() ->
      submissions.submitFromSource(
        UUID.randomUUID(),
        unavailableRequest,
        null,
        SOURCE
      )
    ).isInstanceOf(OrderProductUnavailableException.class);

    createOrders(10);

    assertThat(orderCount()).isEqualTo(10);
  }

  private void createOrders(int count) {
    for (int index = 0; index < count; index++) {
      submissions.submitFromSource(
        UUID.randomUUID(),
        request(),
        null,
        SOURCE
      );
    }
  }

  private long orderCount() {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM orders",
      Long.class
    );
  }

  private CreateOrderRequest request() {
    return new CreateOrderRequest(
      "en",
      new OrderContactRequest(
        "Test Customer",
        "order-limit@example.com",
        null
      ),
      new OrderDeliveryRequest.Digital(),
      List.of(
        new OrderItemRequest.Digital("pattern-001", 1)
      )
    );
  }
}
