package pl.lilimi.inquiry.api.admin;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminInquiryPageResponse(
  List<AdminInquirySummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static AdminInquiryPageResponse from(
    Page<AdminInquirySummaryResponse> inquiries
  ) {
    return new AdminInquiryPageResponse(
      inquiries.getContent(),
      inquiries.getNumber(),
      inquiries.getSize(),
      inquiries.getTotalElements(),
      inquiries.getTotalPages()
    );
  }
}
