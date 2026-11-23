package pl.lilimi.notification.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
  prefix = "lilimi.notifications.admin.worker",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
public class AdminNotificationSchedulingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(
    AdminNotificationSchedulingConfiguration.class
  );

  private static final int BATCH_SIZE = 5;

  private final AdminNotificationProcessor processor;

  public AdminNotificationSchedulingConfiguration(
    AdminNotificationProcessor processor
  ) {
    this.processor = processor;
  }

  @Scheduled(
    initialDelayString =
      "${lilimi.notifications.admin.worker.initial-delay-ms:10000}",
    fixedDelayString =
      "${lilimi.notifications.admin.worker.poll-delay-ms:5000}"
  )
  public void processTelegram() {
    process("TELEGRAM");
  }

  @Scheduled(
    initialDelayString =
      "${lilimi.notifications.admin.worker.initial-delay-ms:10000}",
    fixedDelayString =
      "${lilimi.notifications.admin.worker.poll-delay-ms:5000}"
  )
  public void processEmail() {
    process("EMAIL");
  }

  private void process(String channel) {
    try {
      for (int index = 0; index < BATCH_SIZE; index++) {
        if (!processor.processNext(channel)) {
          return;
        }
      }
    } catch (RuntimeException exception) {
      log.error(
        "Administrator notification worker failed for channel {}. "
          + "Processing will resume on the next scheduled run.",
        channel
      );
    }
  }
}
