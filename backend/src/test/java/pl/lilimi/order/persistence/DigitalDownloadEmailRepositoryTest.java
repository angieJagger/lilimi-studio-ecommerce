package pl.lilimi.order.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class DigitalDownloadEmailRepositoryTest {

  private static final Instant NOW =
    Instant.parse("2026-11-02T10:00:00Z");

  private static final Duration COOLDOWN = Duration.ofMinutes(5);

  @Autowired
  private DigitalDownloadEmailRepository queue;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private JdbcTemplate jdbc;

  private UUID orderId;

  @BeforeEach
  void setUp() {
    var order = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        "customer@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    ));

    orderId = order.getId();
  }

  @Test
  void shouldCreateMissingQueueEntry() {
    assertThat(queue.enqueueAgain(orderId, NOW, COOLDOWN))
      .isTrue();

    var entry = readEntry();

    assertThat(entry.get("status")).isEqualTo("pending");
    assertThat(((Number) entry.get("attempts")).intValue())
      .isZero();
  }

  @Test
  void shouldNotResetPendingEntryEvenAfterCooldown() {
    queue.enqueueAgain(orderId, NOW, COOLDOWN);

    queue.recordFailure(
      orderId,
      true,
      NOW.plusSeconds(600),
      "SMTP_SEND_FAILED"
    );

    assertThat(queue.enqueueAgain(
      orderId,
      NOW.plusSeconds(301),
      COOLDOWN
    )).isFalse();

    var entry = readEntry();

    assertThat(entry.get("status")).isEqualTo("pending");
    assertThat(((Number) entry.get("attempts")).intValue())
      .isEqualTo(1);
    assertThat(entry.get("last_error_code"))
      .isEqualTo("SMTP_SEND_FAILED");
  }

  @Test
  void shouldRejectResendBeforeCooldownExpires() {
    queue.enqueueAgain(orderId, NOW, COOLDOWN);
    queue.markSent(orderId, NOW);

    assertThat(queue.enqueueAgain(
      orderId,
      NOW.plusSeconds(299),
      COOLDOWN
    )).isFalse();

    assertThat(readEntry().get("status")).isEqualTo("sent");
  }

  @Test
  void shouldRequeueAtExactCooldownBoundary() {
    queue.enqueueAgain(orderId, NOW, COOLDOWN);
    queue.markSent(orderId, NOW);

    assertThat(queue.enqueueAgain(
      orderId,
      NOW.plus(COOLDOWN),
      COOLDOWN
    )).isTrue();

    var entry = readEntry();

    assertThat(entry.get("status")).isEqualTo("pending");
    assertThat(((Number) entry.get("attempts")).intValue())
      .isZero();
    assertThat(entry.get("sent_at")).isNull();
    assertThat(entry.get("last_error_code")).isNull();
  }

  @Test
  void shouldCountCooldownFromLaterDeliveryTime() {
    queue.enqueueAgain(orderId, NOW, COOLDOWN);
    queue.markSent(orderId, NOW.plusSeconds(120));

    assertThat(queue.enqueueAgain(
      orderId,
      NOW.plusSeconds(300),
      COOLDOWN
    )).isFalse();

    assertThat(queue.enqueueAgain(
      orderId,
      NOW.plusSeconds(420),
      COOLDOWN
    )).isTrue();
  }

  private java.util.Map<String, Object> readEntry() {
    return jdbc.queryForMap(
      """
      SELECT status, attempts, sent_at, last_error_code
      FROM digital_download_emails
      WHERE order_id = ?
      """,
      orderId
    );
  }
}
