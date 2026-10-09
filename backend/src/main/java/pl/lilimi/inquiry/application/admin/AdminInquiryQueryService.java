package pl.lilimi.inquiry.application.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.inquiry.api.admin.AdminInquiryDetailsResponse;

import java.util.UUID;

@Service
public class AdminInquiryQueryService {

  private final ProjectInquiryRepository inquiries;

  public AdminInquiryQueryService(
    ProjectInquiryRepository inquiries
  ) {
    this.inquiries = inquiries;
  }

  @Transactional(readOnly = true)
  public Page<ProjectInquiry> findInquiries(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid pagination");
    }

    var sorting = Sort.by(
      Sort.Order.desc("createdAt"),
      Sort.Order.desc("id")
    );

    return inquiries.findAll(
      PageRequest.of(page, size, sorting)
    );
  }

  @Transactional(readOnly = true)
  public AdminInquiryDetailsResponse findInquiry(UUID id) {
    var inquiry = inquiries.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Inquiry not found"
      ));

    return AdminInquiryDetailsResponse.from(inquiry);
  }
}
