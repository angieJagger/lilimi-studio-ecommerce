package pl.lilimi.inquiry.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.inquiry.application.ProjectInquiryService;

@RestController
@RequestMapping("/api/project-inquiries")
public class ProjectInquiryController {

  private final ProjectInquiryService service;

  public ProjectInquiryController(ProjectInquiryService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateInquiryResponse createInquiry(
    @Valid @RequestBody CreateInquiryRequest request
  ) {
    return CreateInquiryResponse.from(
      service.createInquiry(request)
    );
  }
}
