package pl.lilimi.payment.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class StripeWebhookEventRepository {

  private final JdbcTemplate jdbc;

  public StripeWebhookEventRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean tryRecord(
    String eventId,
    String eventType,
    boolean livemode
  ) {
    int inserted = jdbc.update(
      """
      INSERT INTO stripe_webhook_events (
        event_id,
        event_type,
        livemode
      )
      VALUES (?, ?, ?)
      ON CONFLICT (event_id) DO NOTHING
      """,
      eventId,
      eventType,
      livemode
    );

    return inserted == 1;
  }
}
