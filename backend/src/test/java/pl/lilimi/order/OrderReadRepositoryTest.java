package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderReadRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderReadRepositoryTest {

  @Autowired
  private OrderReadRepository reads;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldKeepFirstReadTimestampWithoutCreatingDuplicates() {
    UUID administratorId = saveAdministrator();
    UUID orderId = saveOrder();

    assertThat(reads.findReadIds(administratorId, List.of(orderId)))
      .isEmpty();

    reads.markRead(administratorId, orderId);

    Instant firstRead = Instant.parse("2026-01-01T12:00:00Z");

    jdbc.update(
      """
      UPDATE admin_order_reads
      SET read_at = ?
      WHERE administrator_id = ? AND order_id = ?
      """,
      Timestamp.from(firstRead),
      administratorId,
      orderId
    );

    reads.markRead(administratorId, orderId);

    assertThat(reads.findReadIds(administratorId, List.of(orderId)))
      .containsExactly(orderId);

    assertThat(jdbc.queryForObject(
      """
      SELECT COUNT(*) FROM admin_order_reads
      WHERE administrator_id = ? AND order_id = ?
      """,
      Long.class,
      administratorId,
      orderId
    )).isEqualTo(1L);

    var savedTimestamp = jdbc.queryForObject(
      """
      SELECT read_at FROM admin_order_reads
      WHERE administrator_id = ? AND order_id = ?
      """,
      Timestamp.class,
      administratorId,
      orderId
    );

    assertThat(savedTimestamp).isNotNull();
    assertThat(savedTimestamp.toInstant()).isEqualTo(firstRead);
  }

  @Test
  void shouldRestrictReadsToAdministratorAndRequestedOrders() {
    UUID firstAdmin = saveAdministrator();
    UUID secondAdmin = saveAdministrator();
    UUID firstOrder = saveOrder();
    UUID secondOrder = saveOrder();
    UUID unreadOrder = saveOrder();

    reads.markRead(firstAdmin, firstOrder);
    reads.markRead(firstAdmin, secondOrder);
    reads.markRead(secondAdmin, secondOrder);

    var requestedIds = List.of(firstOrder, secondOrder, unreadOrder);

    assertThat(reads.findReadIds(firstAdmin, requestedIds))
      .containsExactlyInAnyOrder(firstOrder, secondOrder);

    assertThat(reads.findReadIds(secondAdmin, requestedIds))
      .containsExactly(secondOrder);

    assertThat(reads.findReadIds(firstAdmin, List.of(secondOrder)))
      .containsExactly(secondOrder);

    assertThat(reads.findReadIds(firstAdmin, List.of()))
      .isEmpty();
  }

  private UUID saveAdministrator() {
    return accounts.saveAndFlush(
      new UserAccount(
        UUID.randomUUID() + "@example.com",
        "test-password-hash",
        UserRole.ADMIN
      )
    ).getId();
  }

  private UUID saveOrder() {
    return orders.saveAndFlush(
      new Order(
        "pl",
        new OrderContact("Anna Kowalska", "anna@example.com", null),
        OrderDelivery.digital(),
        2900,
        0
      )
    ).getId();
  }
}
