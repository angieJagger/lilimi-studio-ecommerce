package pl.lilimi.inquiry.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import pl.lilimi.inquiry.persistence.InquiryAttachmentRepository;
import pl.lilimi.inquiry.storage.InquiryAttachmentStorage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryAttachmentCleanupTest {

  private static final Instant NOW =
    Instant.parse("2026-11-06T12:00:00Z");

  @Mock
  private InquiryAttachmentRepository attachments;

  @Mock
  private InquiryAttachmentStorage storage;

  private InquiryAttachmentCleanup cleanup;

  @BeforeEach
  void setUp() {
    cleanup = new InquiryAttachmentCleanup(
      attachments,
      storage,
      Clock.fixed(NOW, ZoneOffset.UTC),
      Duration.ofHours(24)
    );
  }

  @Test
  void shouldDeleteOrphanAndKeepReferencedAttachment() throws Exception {
    String orphan = "00000000-0000-0000-0000-000000000001.bin";
    String referenced = "00000000-0000-0000-0000-000000000002.bin";
    Instant cutoff = NOW.minus(Duration.ofHours(24));

    when(storage.findStoredBefore(cutoff))
      .thenReturn(List.of(orphan, referenced));

    when(attachments.existsByStorageKey(orphan)).thenReturn(false);
    when(attachments.existsByStorageKey(referenced)).thenReturn(true);

    cleanup.cleanUp();

    verify(storage).findStoredBefore(cutoff);
    verify(storage).delete(orphan);
    verify(storage, never()).delete(referenced);
  }

  @Test
  void shouldStopCleanupWhenDatabaseCheckFails() throws Exception {
    String first = "00000000-0000-0000-0000-000000000001.bin";
    String second = "00000000-0000-0000-0000-000000000002.bin";
    Instant cutoff = NOW.minus(Duration.ofHours(24));

    when(storage.findStoredBefore(cutoff))
      .thenReturn(List.of(first, second));

    when(attachments.existsByStorageKey(first))
      .thenThrow(new DataAccessResourceFailureException(
        "Database unavailable"
      ));

    cleanup.cleanUp();

    verify(storage, never()).delete(first);
    verify(storage, never()).delete(second);
    verify(attachments, never()).existsByStorageKey(second);
  }
}
