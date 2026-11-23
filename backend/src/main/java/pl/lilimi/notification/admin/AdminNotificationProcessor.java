package pl.lilimi.notification.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class AdminNotificationProcessor {

  private static final Logger log = LoggerFactory.getLogger(
    AdminNotificationProcessor.class
  );

  private final AdminNotificationRepository repository;
  private final AdminNotificationMessageFactory messages;
  private final TelegramNotificationSender telegram;
  private final AdminNotificationEmailSender email;
  private final AdminNotificationProperties properties;
  private final Clock clock;

  public AdminNotificationProcessor(
    AdminNotificationRepository repository,
    AdminNotificationMessageFactory messages,
    TelegramNotificationSender telegram,
    AdminNotificationEmailSender email,
    AdminNotificationProperties properties,
    Clock clock
  ) {
    this.repository = repository;
    this.messages = messages;
    this.telegram = telegram;
    this.email = email;
    this.properties = properties;
    this.clock = clock;
  }

  public boolean processNext(String channel) {
    boolean enabled = switch (channel) {
      case "TELEGRAM" -> properties.telegram().enabled();
      case "EMAIL" -> properties.email().enabled();
      default -> throw new IllegalArgumentException(
        "Invalid notification channel"
      );
    };

    if (!enabled) {
      return false;
    }

    var next = repository.claimNext(channel, clock.instant());

    if (next.isEmpty()) {
      return false;
    }

    var claim = next.get();

    try {
      var message = messages.create(claim);

      if (message.isEmpty()) {
        checkUpdate(repository.skip(claim), claim);
        return true;
      }

      switch (claim.channel()) {
        case "TELEGRAM" -> telegram.send(message.get().text());
        case "EMAIL" -> email.send(message.get());
        default -> throw new AdminNotificationDeliveryException(
          "UNKNOWN_CHANNEL",
          false
        );
      }

      checkUpdate(
        repository.complete(claim, clock.instant()),
        claim
      );
    } catch (AdminNotificationDeliveryException exception) {
      if (exception.retryable()) {
        scheduleRetry(claim, exception.errorCode());
      } else {
        checkUpdate(
          repository.fail(claim, exception.errorCode()),
          claim
        );

        log.warn(
          "Administrator notification {} failed: {}",
          claim.id(),
          exception.errorCode()
        );
      }
    } catch (RuntimeException exception) {
      scheduleRetry(claim, "PROCESSING_FAILED");
    }

    return true;
  }

  private void scheduleRetry(
    AdminNotificationRepository.Claim claim,
    String errorCode
  ) {
    long delaySeconds = Math.min(
      3600,
      30L << Math.min(Math.max(claim.attempts() - 1, 0), 7)
    );

    if ("TELEGRAM_RATE_LIMITED".equals(errorCode)) {
      delaySeconds = Math.max(delaySeconds, 60);
    }

    checkUpdate(
      repository.retry(
        claim,
        clock.instant().plusSeconds(delaySeconds),
        errorCode
      ),
      claim
    );

    log.warn(
      "Administrator notification {} delivery failed: {}",
      claim.id(),
      errorCode
    );
  }

  private void checkUpdate(
    boolean updated,
    AdminNotificationRepository.Claim claim
  ) {
    if (!updated) {
      log.warn(
        "Administrator notification {} lease is no longer owned",
        claim.id()
      );
    }
  }
}
