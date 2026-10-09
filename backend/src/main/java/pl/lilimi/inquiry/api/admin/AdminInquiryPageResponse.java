package pl.lilimi.inquiry.api.admin;

import org.springframework.data.domain.Page;
import pl.lilimi.inquiry.domain.ProjectInquiry;

import java.util.List;

public record AdminInquiryPageResponse(
  List<AdminInquirySummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static AdminInquiryPageResponse from(
    Page<ProjectInquiry> inquiries
  ) {
    return new AdminInquiryPageResponse(
      inquiries.getContent().stream()
        .map(AdminInquirySummaryResponse::from)
        .toList(),
      inquiries.getNumber(),
      inquiries.getSize(),
      inquiries.getTotalElements(),
      inquiries.getTotalPages()
    );
  }
}
