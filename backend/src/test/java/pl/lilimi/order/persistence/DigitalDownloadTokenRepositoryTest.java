package pl.lilimi.order.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.DigitalDownloadToken;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class DigitalDownloadTokenRepositoryTest {

  @Autowired
  private DigitalDownloadTokenRepository tokens;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSaveAndFindTokenByHash() {
    var order = createOrder();
    Instant createdAt = Instant.parse("2026-11-01T10:00:00Z");
    Instant expiresAt = createdAt.plus(7, ChronoUnit.DAYS);
    String hash = "a".repeat(64);

    var token = new DigitalDownloadToken(
      order.getId(),
      hash,
      createdAt,
      expiresAt
    );

    tokens.saveAndFlush(token);
    entityManager.clear();

    var saved = tokens.findByTokenHash(hash).orElseThrow();

    assertThat(saved.getId()).isEqualTo(token.getId());
    assertThat(saved.getOrderId()).isEqualTo(order.getId());
    assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(saved.isUsableAt(createdAt)).isTrue();
  }

  @Test
  void shouldPersistRevocation() {
    var order = createOrder();
    Instant createdAt = Instant.parse("2026-11-01T10:00:00Z");
    Instant revokedAt = createdAt.plusSeconds(60);
    String hash = "b".repeat(64);

    var token = new DigitalDownloadToken(
      order.getId(),
      hash,
      createdAt,
      createdAt.plus(7, ChronoUnit.DAYS)
    );

    tokens.saveAndFlush(token);
    entityManager.clear();

    var saved = tokens.findByTokenHash(hash).orElseThrow();
    saved.revoke(revokedAt);

    tokens.flush();
    entityManager.clear();

    var reloaded = tokens.findByTokenHash(hash).orElseThrow();

    assertThat(reloaded.isUsableAt(revokedAt)).isFalse();
  }

  @Test
  void shouldReturnEmptyForUnknownHash() {
    assertThat(tokens.findByTokenHash("c".repeat(64)))
      .isEmpty();
  }

  private Order createOrder() {
    return orders.saveAndFlush(new Order(
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
  }
}
