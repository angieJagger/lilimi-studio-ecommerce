package pl.lilimi.inquiry.api.admin;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.inquiry.application.admin.AdminInquiryAttachmentService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/project-inquiries/{inquiryId}/attachments")
public class AdminInquiryAttachmentController {

  private final AdminInquiryAttachmentService attachments;

  public AdminInquiryAttachmentController(
    AdminInquiryAttachmentService attachments
  ) {
    this.attachments = attachments;
  }

  @GetMapping
  public ResponseEntity<List<AdminInquiryAttachmentResponse>> findAttachments(
    @PathVariable("inquiryId") UUID inquiryId
  ) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(attachments.findAttachments(inquiryId));
  }

  @GetMapping("/{attachmentId}/download")
  public ResponseEntity<Resource> download(
    @PathVariable("inquiryId") UUID inquiryId,
    @PathVariable("attachmentId") UUID attachmentId
  ) {
    var download = attachments.download(inquiryId, attachmentId);

    var disposition = ContentDisposition.attachment()
      .filename(download.filename(), StandardCharsets.UTF_8)
      .build();

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(download.contentType()))
      .contentLength(download.byteSize())
      .cacheControl(CacheControl.noStore())
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        disposition.toString()
      )
      .header("X-Content-Type-Options", "nosniff")
      .body(download.resource());
  }
}
