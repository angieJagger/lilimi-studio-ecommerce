package pl.lilimi.inquiry.application.admin;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.inquiry.api.admin.AdminInquiryAttachmentResponse;
import pl.lilimi.inquiry.persistence.InquiryAttachmentRepository;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import pl.lilimi.inquiry.storage.InquiryAttachmentStorage;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class AdminInquiryAttachmentService {

  private final ProjectInquiryRepository inquiries;
  private final InquiryAttachmentRepository attachments;
  private final InquiryAttachmentStorage storage;

  public AdminInquiryAttachmentService(
    ProjectInquiryRepository inquiries,
    InquiryAttachmentRepository attachments,
    InquiryAttachmentStorage storage
  ) {
    this.inquiries = inquiries;
    this.attachments = attachments;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public List<AdminInquiryAttachmentResponse> findAttachments(
    UUID inquiryId
  ) {
    if (!inquiries.existsById(inquiryId)) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Inquiry not found"
      );
    }

    return attachments.findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiryId)
      .stream()
      .map(AdminInquiryAttachmentResponse::from)
      .toList();
  }

  @Transactional(readOnly = true)
  public Download download(UUID inquiryId, UUID attachmentId) {
    var attachment = attachments.findByIdAndInquiryId(
      attachmentId,
      inquiryId
    ).orElseThrow(() -> new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Inquiry attachment not found"
    ));

    try {
      return new Download(
        storage.load(attachment.getStorageKey()),
        attachment.getOriginalFilename(),
        attachment.getContentType(),
        attachment.getByteSize()
      );
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Inquiry attachment is temporarily unavailable",
        exception
      );
    }
  }

  public record Download(
    Resource resource,
    String filename,
    String contentType,
    long byteSize
  ) {
  }
}
