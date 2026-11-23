package pl.lilimi.notification.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
  "lilimi.notifications.admin.telegram.enabled=true",
  "lilimi.notifications.admin.telegram.bot-token=test-only-token",
  "lilimi.notifications.admin.telegram.chat-id=123456",
  "lilimi.notifications.admin.email.enabled=true",
  "lilimi.notifications.admin.email.recipient=admin@example.com"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminNotificationQueueTest {

  @Autowired
  private AdminNotificationQueue queue;

  @Autowired
  private AdminNotificationRepository repository;

  @Autowired
  private JdbcTemplate jdbc;

  private Instant now;

  @BeforeEach
  void prepareQueue() {
    jdbc.update("DELETE FROM admin_notifications");
    now = Instant.now().plusSeconds(60);
  }

  @Test
  void shouldDeduplicateEachEventAndChannel() {
    UUID inquiryId = UUID.randomUUID();

    assertThat(queue.enqueueInquiry(inquiryId)).isEqualTo(2);
    assertThat(queue.enqueueInquiry(inquiryId)).isZero();

    assertThat(jdbc.queryForObject(
      "SELECT COUNT(*) FROM admin_notifications",
      Long.class
    )).isEqualTo(2L);
  }

  @Test
  void shouldKeepChannelsIndependent() {
    queue.enqueuePaidOrder(UUID.randomUUID());

    var telegram = repository.claimNext("TELEGRAM", now).orElseThrow();
    var email = repository.claimNext("EMAIL", now).orElseThrow();

    assertThat(repository.complete(telegram, now)).isTrue();
    assertThat(repository.retry(
      email,
      now.plusSeconds(60),
      "SMTP_UNAVAILABLE"
    )).isTrue();

    assertThat(repository.claimNext("TELEGRAM", now)).isEmpty();
    assertThat(repository.claimNext("EMAIL", now)).isEmpty();
    assertThat(repository.claimNext("EMAIL", now.plusSeconds(61)))
      .isPresent();
  }

  @Test
  void shouldNotClaimTaskWhileItsLeaseIsActive() {
    queue.enqueueInquiry(UUID.randomUUID());

    var claim = repository.claimNext("TELEGRAM", now).orElseThrow();

    assertThat(claim.attempts()).isEqualTo(1);
    assertThat(repository.claimNext(
      "TELEGRAM",
      now.plusSeconds(119)
    )).isEmpty();
  }

  @Test
  void shouldRecoverExpiredLeaseAndRejectOldWorkerUpdates() {
    queue.enqueueInquiry(UUID.randomUUID());

    var original = repository.claimNext("TELEGRAM", now).orElseThrow();
    Instant later = now.plusSeconds(121);

    var recovered = repository.claimNext("TELEGRAM", later)
      .orElseThrow();

    assertThat(recovered.id()).isEqualTo(original.id());
    assertThat(recovered.token()).isNotEqualTo(original.token());
    assertThat(recovered.attempts()).isEqualTo(2);

    assertThat(repository.complete(original, later)).isFalse();
    assertThat(repository.retry(
      original,
      later,
      "STALE_WORKER"
    )).isFalse();

    assertThat(repository.complete(recovered, later)).isTrue();
  }

  @Test
  void shouldStopAfterEightFailedAttempts() {
    queue.enqueueInquiry(UUID.randomUUID());

    for (int attempt = 1; attempt <= 8; attempt++) {
      var claim = repository.claimNext("TELEGRAM", now).orElseThrow();

      assertThat(claim.attempts()).isEqualTo(attempt);
      assertThat(repository.retry(
        claim,
        now,
        "DELIVERY_FAILED"
      )).isTrue();
    }

    assertThat(repository.claimNext("TELEGRAM", now)).isEmpty();

    assertThat(jdbc.queryForObject("""
      SELECT status
      FROM admin_notifications
      WHERE channel = 'TELEGRAM'
      """,
      String.class
    )).isEqualTo("FAILED");
  }

  @Test
  void shouldFailExpiredLeaseAfterLastAttempt() {
    queue.enqueueInquiry(UUID.randomUUID());

    for (int attempt = 1; attempt <= 8; attempt++) {
      var claim = repository.claimNext("TELEGRAM", now).orElseThrow();

      assertThat(claim.attempts()).isEqualTo(attempt);
      now = now.plusSeconds(121);
    }

    assertThat(repository.claimNext("TELEGRAM", now)).isEmpty();

    assertThat(jdbc.queryForObject("""
      SELECT status
      FROM admin_notifications
      WHERE channel = 'TELEGRAM'
      """,
      String.class
    )).isEqualTo("FAILED");
  }
}
