package pl.lilimi.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.payment.persistence.StripeWebhookEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class StripeWebhookEventRepositoryTest {

  @Autowired
  private StripeWebhookEventRepository events;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  void shouldRecordEventOnlyOnce() {
    String eventId = newEventId();
    var transaction = new TransactionTemplate(
      transactionManager
    );

    try {
      transaction.executeWithoutResult(status -> {
        assertThat(events.tryRecord(
          eventId,
          "checkout.session.completed",
          false
        )).isTrue();

        assertThat(events.tryRecord(
          eventId,
          "checkout.session.completed",
          false
        )).isFalse();
      });

      assertThat(count(eventId)).isEqualTo(1L);
    } finally {
      delete(eventId);
    }
  }

  @Test
  void shouldAllowRetryAfterTransactionRollback() {
    String eventId = newEventId();
    var transaction = new TransactionTemplate(
      transactionManager
    );

    try {
      transaction.executeWithoutResult(status -> {
        assertThat(events.tryRecord(
          eventId,
          "checkout.session.completed",
          false
        )).isTrue();

        status.setRollbackOnly();
      });

      assertThat(count(eventId)).isZero();

      transaction.executeWithoutResult(status ->
        assertThat(events.tryRecord(
          eventId,
          "checkout.session.completed",
          false
        )).isTrue()
      );

      assertThat(count(eventId)).isEqualTo(1L);
    } finally {
      delete(eventId);
    }
  }

  @Test
  void shouldRequireExistingTransaction() {
    String eventId = newEventId();

    assertThatThrownBy(() ->
      events.tryRecord(
        eventId,
        "checkout.session.completed",
        false
      )
    ).isInstanceOf(IllegalTransactionStateException.class);

    assertThat(count(eventId)).isZero();
  }

  private long count(String eventId) {
    return jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM stripe_webhook_events
      WHERE event_id = ?
      """,
      Long.class,
      eventId
    );
  }

  private void delete(String eventId) {
    jdbc.update(
      "DELETE FROM stripe_webhook_events WHERE event_id = ?",
      eventId
    );
  }

  private static String newEventId() {
    return "evt_" + UUID.randomUUID();
  }
}
