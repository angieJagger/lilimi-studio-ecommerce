package pl.lilimi.inquiry.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pl.lilimi.inquiry.application.admin.AdminInquiryQueryService;
import pl.lilimi.inquiry.application.admin.AdminInquiryStatusService;

import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/project-inquiries")
public class AdminInquiryController {

  private final AdminInquiryQueryService queryService;
  private final AdminInquiryStatusService statusService;

  public AdminInquiryController(
    AdminInquiryQueryService queryService,
    AdminInquiryStatusService statusService
  ) {
    this.queryService = queryService;
    this.statusService = statusService;
  }

  @GetMapping
  public AdminInquiryPageResponse findInquiries(
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
    @RequestParam(defaultValue = "all") String view,
    @RequestParam(required = false) String status,
    @RequestParam(required = false) String projectType,
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateFrom,
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateTo,
    @RequestParam(required = false) String search
  ) {
    return AdminInquiryPageResponse.from(
      queryService.findInquiries(
        page,
        size,
        view,
        status,
        projectType,
        dateFrom,
        dateTo,
        search
      )
    );
  }

  @GetMapping("/{id}")
  public AdminInquiryDetailsResponse findInquiry(
    @PathVariable UUID id
  ) {
    return queryService.findInquiry(id);
  }

  @PatchMapping("/{id}/status")
  public AdminInquiryDetailsResponse changeStatus(
    @PathVariable UUID id,
    @Valid @RequestBody ChangeInquiryStatusRequest request
  ) {
    return statusService.changeStatus(id, request);
  }
}
