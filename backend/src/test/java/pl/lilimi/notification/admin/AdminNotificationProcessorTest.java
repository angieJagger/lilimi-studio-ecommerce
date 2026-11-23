package pl.lilimi.notification.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationProcessorTest {

  private static final Instant NOW =
    Instant.parse("2026-11-23T12:00:00Z");

  @Mock
  private AdminNotificationRepository repository;

  @Mock
  private AdminNotificationMessageFactory messages;

  @Mock
  private TelegramNotificationSender telegram;

  @Mock
  private AdminNotificationEmailSender email;

  private AdminNotificationProcessor processor;

  private final AdminNotificationMessage message =
    new AdminNotificationMessage("Subject", "Notification text");

  @BeforeEach
  void setUp() {
    var properties = new AdminNotificationProperties(
      new AdminNotificationProperties.Telegram(
        true, "test-only-token", "123456"
      ),
      new AdminNotificationProperties.Email(
        true, "admin@example.com"
      )
    );

    processor = new AdminNotificationProcessor(
      repository,
      messages,
      telegram,
      email,
      properties,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void shouldCompleteSuccessfulTelegramDelivery() {
    var claim = pending("TELEGRAM", 1);
    when(messages.create(claim)).thenReturn(Optional.of(message));
    when(repository.complete(claim, NOW)).thenReturn(true);

    assertThat(processor.processNext("TELEGRAM")).isTrue();

    verify(telegram).send(message.text());
    verify(repository).complete(claim, NOW);
    verify(repository, never()).retry(any(), any(), any());
    verifyNoInteractions(email);
  }

  @Test
  void shouldSendEmailThroughSeparateChannel() {
    var claim = pending("EMAIL", 1);
    when(messages.create(claim)).thenReturn(Optional.of(message));
    when(repository.complete(claim, NOW)).thenReturn(true);

    assertThat(processor.processNext("EMAIL")).isTrue();

    verify(email).send(message);
    verify(repository).complete(claim, NOW);
    verifyNoInteractions(telegram);
  }

  @Test
  void shouldSkipUnavailableResource() {
    var claim = pending("TELEGRAM", 1);
    when(messages.create(claim)).thenReturn(Optional.empty());
    when(repository.skip(claim)).thenReturn(true);

    assertThat(processor.processNext("TELEGRAM")).isTrue();

    verify(repository).skip(claim);
    verifyNoInteractions(telegram, email);
  }

  @Test
  void shouldRetryTemporaryFailureAfterThirtySeconds() {
    var claim = pending("TELEGRAM", 1);
    when(messages.create(claim)).thenReturn(Optional.of(message));

    doThrow(new AdminNotificationDeliveryException(
      "TELEGRAM_CONNECTION_FAILED", true
    )).when(telegram).send(message.text());

    when(repository.retry(
      claim,
      NOW.plusSeconds(30),
      "TELEGRAM_CONNECTION_FAILED"
    )).thenReturn(true);

    processor.processNext("TELEGRAM");

    verify(repository).retry(
      claim,
      NOW.plusSeconds(30),
      "TELEGRAM_CONNECTION_FAILED"
    );
    verify(repository, never()).complete(any(), any());
    verifyNoInteractions(email);
  }

  @Test
  void shouldIncreaseDelayForSubsequentAttempts() {
    var claim = pending("TELEGRAM", 3);
    when(messages.create(claim)).thenReturn(Optional.of(message));

    doThrow(new AdminNotificationDeliveryException(
      "TELEGRAM_CONNECTION_FAILED", true
    )).when(telegram).send(message.text());

    when(repository.retry(
      claim,
      NOW.plusSeconds(120),
      "TELEGRAM_CONNECTION_FAILED"
    )).thenReturn(true);

    processor.processNext("TELEGRAM");

    verify(repository).retry(
      claim,
      NOW.plusSeconds(120),
      "TELEGRAM_CONNECTION_FAILED"
    );
  }

  @Test
  void shouldFailPermanentlyForInvalidCredentials() {
    var claim = pending("TELEGRAM", 1);
    when(messages.create(claim)).thenReturn(Optional.of(message));

    doThrow(new AdminNotificationDeliveryException(
      "TELEGRAM_HTTP_401", false
    )).when(telegram).send(message.text());

    when(repository.fail(claim, "TELEGRAM_HTTP_401"))
      .thenReturn(true);

    processor.processNext("TELEGRAM");

    verify(repository).fail(claim, "TELEGRAM_HTTP_401");
    verify(repository, never()).retry(any(), any(), any());
    verify(repository, never()).complete(any(), any());
  }

  @Test
  void shouldReturnFalseWhenQueueIsEmpty() {
    when(repository.claimNext("TELEGRAM", NOW))
      .thenReturn(Optional.empty());

    assertThat(processor.processNext("TELEGRAM")).isFalse();

    verifyNoInteractions(messages, telegram, email);
  }

  private AdminNotificationRepository.Claim pending(
    String channel,
    int attempts
  ) {
    var claim = new AdminNotificationRepository.Claim(
      UUID.randomUUID(),
      "INQUIRY_CREATED",
      UUID.randomUUID(),
      channel,
      attempts,
      UUID.randomUUID()
    );

    when(repository.claimNext(channel, NOW))
      .thenReturn(Optional.of(claim));

    return claim;
  }
}
