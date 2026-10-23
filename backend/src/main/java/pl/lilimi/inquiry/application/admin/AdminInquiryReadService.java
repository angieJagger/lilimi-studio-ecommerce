package pl.lilimi.inquiry.application.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.inquiry.persistence.InquiryReadRepository;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import java.util.UUID;

@Service
public class AdminInquiryReadService {

  private final ProjectInquiryRepository inquiries;
  private final InquiryReadRepository reads;

  public AdminInquiryReadService(
    ProjectInquiryRepository inquiries,
    InquiryReadRepository reads
  ) {
    this.inquiries = inquiries;
    this.reads = reads;
  }

  @Transactional
  public void markRead(UUID administratorId, UUID inquiryId) {
    if (!inquiries.existsById(inquiryId)) {
      throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Inquiry not found"
      );
    }

    reads.markRead(administratorId, inquiryId);
  }
}
