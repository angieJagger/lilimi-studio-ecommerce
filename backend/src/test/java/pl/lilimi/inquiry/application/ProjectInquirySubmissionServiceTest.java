package pl.lilimi.inquiry.application;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.api.CreateInquiryRequest;
import pl.lilimi.inquiry.persistence.InquiryAttachmentRepository;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import pl.lilimi.inquiry.storage.InquiryAttachmentStorage;

import java.io.IOException;
import java.util.List;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class ProjectInquirySubmissionServiceTest {

  @Autowired
  private ProjectInquirySubmissionService service;

  @Autowired
  private ProjectInquiryRepository inquiries;

  @Autowired
  private InquiryAttachmentRepository attachments;

  @Autowired
  private InquiryAttachmentProcessingGate processingGate;

  @MockitoBean
  private InquiryAttachmentValidator validator;

  @MockitoBean
  private InquiryAttachmentStorage storage;

  @Test
  void shouldRollBackInquiryAndRemoveStoredFileWhenNextUploadFails()
    throws Exception {

    long initialInquiryCount = inquiries.count();
    long initialAttachmentCount = attachments.count();

    var firstFile = new MockMultipartFile(
      "files",
      "first.png",
      "image/png",
      new byte[] { 1 }
    );

    var secondFile = new MockMultipartFile(
      "files",
      "second.png",
      "image/png",
      new byte[] { 2 }
    );

    byte[] firstContent = { 1 };
    byte[] secondContent = { 2 };

    when(validator.validate(firstFile)).thenReturn(
      new ValidatedInquiryAttachment(
        "first.png",
        "image/png",
        firstContent
      )
    );

    when(validator.validate(secondFile)).thenReturn(
      new ValidatedInquiryAttachment(
        "second.png",
        "image/png",
        secondContent
      )
    );

    String storedKey = "00000000-0000-0000-0000-000000000001.bin";

    when(storage.store(firstContent)).thenReturn(storedKey);
    when(storage.store(secondContent)).thenThrow(
      new IOException("Storage unavailable")
    );

    var request = new CreateInquiryRequest(
      "pl",
      "Anna Kowalska",
      "anna@example.com",
      "digitizing",
      "Proszę o wycenę digitalizacji logo.",
      null,
      null
    );

    assertThatThrownBy(() ->
      service.submit(request, List.of(firstFile, secondFile))
    ).isInstanceOfSatisfying(
      ResponseStatusException.class,
      exception -> assertThat(exception.getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    );

    TestTransaction.flagForRollback();
    TestTransaction.end();

    verify(storage).delete(storedKey);

    assertThat(inquiries.count()).isEqualTo(initialInquiryCount);
    assertThat(attachments.count()).isEqualTo(initialAttachmentCount);
  }

  @Test
  void shouldRejectAttachmentsWhenProcessingIsBusy() {
    long initialInquiryCount = inquiries.count();
    long initialAttachmentCount = attachments.count();

    var file = new MockMultipartFile(
      "files",
      "logo.png",
      "image/png",
      new byte[] { 1 }
    );

    processingGate.execute(() -> {
      assertThatThrownBy(() ->
        service.submit(validRequest(), List.of(file))
      ).isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> assertThat(exception.getStatusCode())
          .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      );

      return null;
    });

    verifyNoInteractions(validator, storage);
    assertThat(inquiries.count()).isEqualTo(initialInquiryCount);
    assertThat(attachments.count()).isEqualTo(initialAttachmentCount);
  }

  @Test
  void shouldAcceptInquiryWithoutAttachmentsWhenProcessingIsBusy() {
    var inquiry = processingGate.execute(() ->
      service.submit(validRequest(), List.of())
    );

    assertThat(inquiries.findById(inquiry.getId())).isPresent();
    assertThat(
      attachments.findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiry.getId())
    ).isEmpty();

    verifyNoInteractions(validator, storage);
  }

  @Test
  void shouldSaveAttachmentMetadataForCreatedInquiry() throws Exception {
    var file = new MockMultipartFile(
      "files",
      "logo.png",
      "image/png",
      new byte[] { 1, 2, 3 }
    );

    byte[] content = { 1, 2, 3 };
    String storageKey = "00000000-0000-0000-0000-000000000002.bin";

    when(validator.validate(file)).thenReturn(
      new ValidatedInquiryAttachment(
        "logo.png",
        "image/png",
        content
      )
    );

    when(storage.store(content)).thenReturn(storageKey);

    var request = new CreateInquiryRequest(
      "pl",
      "Anna Kowalska",
      "anna@example.com",
      "digitizing",
      "Proszę o wycenę digitalizacji logo.",
      null,
      null
    );

    var inquiry = service.submit(request, List.of(file));

    var saved = attachments
      .findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiry.getId());

    assertThat(inquiries.findById(inquiry.getId())).isPresent();
    assertThat(saved).hasSize(1);

    var attachment = saved.getFirst();

    assertThat(attachment.getInquiryId()).isEqualTo(inquiry.getId());
    assertThat(attachment.getOriginalFilename()).isEqualTo("logo.png");
    assertThat(attachment.getContentType()).isEqualTo("image/png");
    assertThat(attachment.getByteSize()).isEqualTo(3);
    assertThat(attachment.getStorageKey()).isEqualTo(storageKey);
    assertThat(attachment.getCreatedAt()).isNotNull();
  }

  @Test
  void shouldRejectMoreThanFiveAttachmentsBeforeProcessingFiles() {
    long initialInquiryCount = inquiries.count();
    long initialAttachmentCount = attachments.count();

    var file = new MockMultipartFile(
      "files",
      "logo.png",
      "image/png",
      new byte[] { 1 }
    );

    var request = new CreateInquiryRequest(
      "pl",
      "Anna Kowalska",
      "anna@example.com",
      "digitizing",
      "Proszę o wycenę digitalizacji logo.",
      null,
      null
    );

    assertThatThrownBy(() ->
      service.submit(request, Collections.nCopies(6, file))
    ).isInstanceOfSatisfying(
      ResponseStatusException.class,
      exception -> assertThat(exception.getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST)
    );

    TestTransaction.flagForRollback();
    TestTransaction.end();

    verifyNoInteractions(validator, storage);

    assertThat(inquiries.count()).isEqualTo(initialInquiryCount);
    assertThat(attachments.count()).isEqualTo(initialAttachmentCount);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void shouldKeepStoredFileAfterTransactionCommits() throws Exception {
    var file = new MockMultipartFile(
      "files",
      "logo.png",
      "image/png",
      new byte[] { 4, 5, 6 }
    );

    byte[] content = { 4, 5, 6 };
    String storageKey = "00000000-0000-0000-0000-000000000003.bin";

    when(validator.validate(file)).thenReturn(
      new ValidatedInquiryAttachment(
        "logo.png",
        "image/png",
        content
      )
    );

    when(storage.store(content)).thenReturn(storageKey);

    var request = new CreateInquiryRequest(
      "pl",
      "Anna Kowalska",
      "anna@example.com",
      "digitizing",
      "Proszę o wycenę digitalizacji logo.",
      null,
      null
    );

    var inquiry = service.submit(request, List.of(file));

    try {
      var saved = attachments
        .findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiry.getId());

      assertThat(inquiries.findById(inquiry.getId())).isPresent();
      assertThat(saved).hasSize(1);
      assertThat(saved.getFirst().getStorageKey()).isEqualTo(storageKey);

      verify(storage, never()).delete(storageKey);
    } finally {
      var saved = attachments
        .findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiry.getId());

      attachments.deleteAll(saved);
      inquiries.deleteById(inquiry.getId());
    }
  }

  private CreateInquiryRequest validRequest() {
    return new CreateInquiryRequest(
      "pl",
      "Anna Kowalska",
      "anna@example.com",
      "digitizing",
      "Proszę o wycenę digitalizacji logo.",
      null,
      null
    );
  }
}
