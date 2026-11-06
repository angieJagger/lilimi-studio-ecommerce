package pl.lilimi.inquiry.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.lilimi.inquiry.persistence.InquiryAttachmentRepository;
import pl.lilimi.inquiry.storage.InquiryAttachmentStorage;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Component
public class InquiryAttachmentCleanup {

  private static final Logger LOGGER =
    LoggerFactory.getLogger(InquiryAttachmentCleanup.class);

  private final InquiryAttachmentRepository attachments;
  private final InquiryAttachmentStorage storage;
  private final Clock clock;
  private final Duration minimumAge;

  public InquiryAttachmentCleanup(
    InquiryAttachmentRepository attachments,
    InquiryAttachmentStorage storage,
    Clock clock,
    @Value("${lilimi.inquiry.attachments.cleanup-min-age:PT24H}")
    Duration minimumAge
  ) {
    if (minimumAge.compareTo(Duration.ofHours(1)) < 0) {
      throw new IllegalArgumentException(
        "Attachment cleanup minimum age must be at least one hour"
      );
    }

    this.attachments = attachments;
    this.storage = storage;
    this.clock = clock;
    this.minimumAge = minimumAge;
  }

  @Scheduled(
    initialDelayString =
      "${lilimi.inquiry.attachments.cleanup-delay-ms:3600000}",
    fixedDelayString =
      "${lilimi.inquiry.attachments.cleanup-delay-ms:3600000}"
  )
  public void cleanUp() {
    try {
      var cutoff = clock.instant().minus(minimumAge);

      for (String storageKey : storage.findStoredBefore(cutoff)) {
        if (attachments.existsByStorageKey(storageKey)) {
          continue;
        }

        try {
          storage.delete(storageKey);
        } catch (IOException exception) {
          LOGGER.error(
            "Cannot remove orphaned inquiry attachment: {}",
            storageKey,
            exception
          );
        }
      }
    } catch (IOException | DataAccessException exception) {
      LOGGER.error(
        "Orphaned inquiry attachment cleanup failed",
        exception
      );
    }
  }
}
