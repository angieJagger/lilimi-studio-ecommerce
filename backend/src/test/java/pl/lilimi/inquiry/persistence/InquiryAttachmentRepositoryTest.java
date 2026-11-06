package pl.lilimi.inquiry.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.api.CreateInquiryRequest;
import pl.lilimi.inquiry.application.ProjectInquiryService;
import pl.lilimi.inquiry.domain.InquiryAttachment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class InquiryAttachmentRepositoryTest {

  @Autowired
  private ProjectInquiryService inquiries;

  @Autowired
  private InquiryAttachmentRepository attachments;

  @Test
  void shouldFindAttachmentOnlyWithinItsOwnInquiry() {
    var request = new CreateInquiryRequest(
      "pl",
      "Anna Kowalska",
      "anna@example.com",
      "digitizing",
      "Proszę o wycenę digitalizacji logo.",
      null,
      null
    );

    var ownerInquiry = inquiries.createInquiry(request);
    var otherInquiry = inquiries.createInquiry(request);

    var attachment = attachments.saveAndFlush(
      new InquiryAttachment(
        ownerInquiry.getId(),
        "logo.png",
        "00000000-0000-0000-0000-000000000006.bin",
        "image/png",
        3
      )
    );

    var found = attachments.findByIdAndInquiryId(
      attachment.getId(),
      ownerInquiry.getId()
    );

    assertThat(found).isPresent();
    assertThat(found.orElseThrow().getId())
      .isEqualTo(attachment.getId());

    assertThat(attachments.findByIdAndInquiryId(
      attachment.getId(),
      otherInquiry.getId()
    )).isEmpty();

    assertThat(
      attachments.findAllByInquiryIdOrderByCreatedAtAscIdAsc(
        ownerInquiry.getId()
      )
    ).extracting(InquiryAttachment::getId)
      .containsExactly(attachment.getId());

    assertThat(
      attachments.findAllByInquiryIdOrderByCreatedAtAscIdAsc(
        otherInquiry.getId()
      )
    ).isEmpty();
  }
}
