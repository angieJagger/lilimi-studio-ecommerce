package pl.lilimi.order.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderSubmissionRepositoryTest {

  @Autowired
  private OrderSubmissionRepository submissions;

  @Autowired
  private OrderRepository orders;

  @Test
  void shouldAllowOnlyGuestWhoOwnsSubmittedOrder() {
    var order = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact("Test Customer", "customer@example.com", null),
      OrderDelivery.digital(),
      2900,
      0
    ));

    UUID owner = UUID.randomUUID();
    UUID key = UUID.randomUUID();

    submissions.reserve(key, "a".repeat(64), owner);
    submissions.attachOrder(key, order.getId());

    assertThat(submissions.belongsToGuest(order.getId(), owner)).isTrue();
    assertThat(submissions.belongsToGuest(order.getId(), UUID.randomUUID())).isFalse();
    assertThat(submissions.belongsToGuest(UUID.randomUUID(), owner)).isFalse();
  }
}
