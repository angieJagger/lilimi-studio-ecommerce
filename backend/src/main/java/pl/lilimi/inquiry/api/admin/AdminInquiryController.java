package pl.lilimi.inquiry.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.inquiry.application.admin.AdminInquiryQueryService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/project-inquiries")
public class AdminInquiryController {

  private final AdminInquiryQueryService queryService;

  public AdminInquiryController(
    AdminInquiryQueryService queryService
  ) {
    this.queryService = queryService;
  }

  @GetMapping
  public AdminInquiryPageResponse findInquiries(
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
  ) {
    return AdminInquiryPageResponse.from(
      queryService.findInquiries(page, size)
    );
  }

  @GetMapping("/{id}")
  public AdminInquiryDetailsResponse findInquiry(
    @PathVariable UUID id
  ) {
    return queryService.findInquiry(id);
  }
}
