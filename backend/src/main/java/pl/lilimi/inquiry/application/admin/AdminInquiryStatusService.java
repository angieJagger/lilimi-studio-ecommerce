package pl.lilimi.inquiry.application.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.inquiry.api.admin.AdminInquiryDetailsResponse;
import pl.lilimi.inquiry.api.admin.ChangeInquiryStatusRequest;
import pl.lilimi.inquiry.domain.InquiryStatus;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import java.util.UUID;

@Service
@Validated
public class AdminInquiryStatusService {

  private final ProjectInquiryRepository repository;

  public AdminInquiryStatusService(
    ProjectInquiryRepository repository
  ) {
    this.repository = repository;
  }

  @Transactional
  public AdminInquiryDetailsResponse changeStatus(
    @NotNull UUID id,
    @NotNull @Valid ChangeInquiryStatusRequest request
  ) {
    var inquiry = repository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Inquiry not found"
      ));

    if (inquiry.getVersion() != request.expectedVersion()) {
      throw new InquiryStatusConflictException(
        "INQUIRY_VERSION_CONFLICT",
        "Inquiry was modified. Reload its details before updating."
      );
    }

    var nextStatus = InquiryStatus.fromValue(request.status());

    try {
      inquiry.changeStatus(nextStatus);
    } catch (IllegalStateException exception) {
      throw new InquiryStatusConflictException(
        "INQUIRY_STATUS_TRANSITION_INVALID",
        "Inquiry status transition is not allowed"
      );
    }

    repository.flush();

    return AdminInquiryDetailsResponse.from(inquiry);
  }
}
