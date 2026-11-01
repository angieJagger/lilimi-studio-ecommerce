package pl.lilimi.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
  prefix = "lilimi.mail.digital-downloads",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
public class DigitalDownloadEmailSchedulingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(
    DigitalDownloadEmailSchedulingConfiguration.class
  );

  private static final int BATCH_SIZE = 10;

  private final DigitalDownloadEmailProcessor processor;

  public DigitalDownloadEmailSchedulingConfiguration(
    DigitalDownloadEmailProcessor processor
  ) {
    this.processor = processor;
  }

  @Scheduled(
    initialDelayString =
      "${lilimi.mail.digital-downloads.initial-delay-ms:10000}",
    fixedDelayString =
      "${lilimi.mail.digital-downloads.poll-delay-ms:10000}"
  )
  public void processQueue() {
    try {
      for (int index = 0; index < BATCH_SIZE; index++) {
        if (!processor.processNext()) {
          return;
        }
      }
    } catch (RuntimeException exception) {
      log.error(
        "Digital download email queue processing failed. "
          + "Processing will be attempted again on the next scheduled run."
      );
    }
  }
}
