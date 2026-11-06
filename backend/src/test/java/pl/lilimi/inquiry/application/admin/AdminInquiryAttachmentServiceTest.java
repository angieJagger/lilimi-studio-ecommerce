package pl.lilimi.inquiry.application.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.inquiry.domain.InquiryAttachment;
import pl.lilimi.inquiry.persistence.InquiryAttachmentRepository;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import pl.lilimi.inquiry.storage.InquiryAttachmentStorage;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInquiryAttachmentServiceTest {

  @Mock
  private ProjectInquiryRepository inquiries;

  @Mock
  private InquiryAttachmentRepository attachments;

  @Mock
  private InquiryAttachmentStorage storage;

  private AdminInquiryAttachmentService service;

  @BeforeEach
  void setUp() {
    service = new AdminInquiryAttachmentService(
      inquiries,
      attachments,
      storage
    );
  }

  @Test
  void shouldRejectAttachmentBelongingToAnotherInquiry() {
    UUID ownerInquiryId = UUID.randomUUID();
    UUID requestedInquiryId = UUID.randomUUID();
    UUID attachmentId = UUID.randomUUID();

    var attachment = new InquiryAttachment(
      ownerInquiryId,
      "logo.png",
      "00000000-0000-0000-0000-000000000005.bin",
      "image/png",
      3
    );

    when(attachments.findByIdAndInquiryId(attachmentId, ownerInquiryId))
      .thenReturn(Optional.of(attachment));

    when(attachments.findByIdAndInquiryId(attachmentId, requestedInquiryId))
      .thenReturn(Optional.empty());

    assertThat(
      attachments.findByIdAndInquiryId(attachmentId, ownerInquiryId)
    ).contains(attachment);

    assertThatThrownBy(() ->
      service.download(requestedInquiryId, attachmentId)
    ).isInstanceOfSatisfying(
      ResponseStatusException.class,
      exception -> assertThat(exception.getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND)
    );

    verify(attachments).findByIdAndInquiryId(
      attachmentId,
      requestedInquiryId
    );

    verifyNoInteractions(storage);
  }
}
