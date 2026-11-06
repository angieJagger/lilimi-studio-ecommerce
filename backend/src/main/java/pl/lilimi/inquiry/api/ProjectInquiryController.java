package pl.lilimi.inquiry.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import pl.lilimi.inquiry.application.ProjectInquirySubmissionService;
import pl.lilimi.inquiry.application.ProjectInquiryService;

@RestController
@RequestMapping("/api/project-inquiries")
public class ProjectInquiryController {

  private final ProjectInquiryService service;
  private final ProjectInquirySubmissionService submissions;

  public ProjectInquiryController(
    ProjectInquiryService service,
    ProjectInquirySubmissionService submissions
  ) {
    this.service = service;
    this.submissions = submissions;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public CreateInquiryResponse createInquiry(
    @Valid @RequestBody CreateInquiryRequest request
  ) {
    return CreateInquiryResponse.from(
      service.createInquiry(request)
    );
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public CreateInquiryResponse createInquiryWithAttachments(
    @Valid @RequestPart("request") CreateInquiryRequest request,
    @RequestPart(value = "files", required = false)
    List<MultipartFile> files
  ) {
    return CreateInquiryResponse.from(
      submissions.submit(
        request,
        files == null ? List.of() : files
      )
    );
  }
}
