package pl.lilimi.inquiry.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.inquiry.api.CreateInquiryRequest;
import pl.lilimi.inquiry.domain.InquiryAttachment;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.InquiryAttachmentRepository;
import pl.lilimi.inquiry.storage.InquiryAttachmentStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Validated
public class ProjectInquirySubmissionService {

  private static final Logger LOGGER =
    LoggerFactory.getLogger(ProjectInquirySubmissionService.class);

  private static final int MAX_ATTACHMENTS = 5;

  private final ProjectInquiryService inquiries;
  private final InquiryAttachmentValidator validator;
  private final InquiryAttachmentStorage storage;
  private final InquiryAttachmentRepository attachments;

  public ProjectInquirySubmissionService(
    ProjectInquiryService inquiries,
    InquiryAttachmentValidator validator,
    InquiryAttachmentStorage storage,
    InquiryAttachmentRepository attachments
  ) {
    this.inquiries = inquiries;
    this.validator = validator;
    this.storage = storage;
    this.attachments = attachments;
  }

  @Transactional(timeout = 120)
  public ProjectInquiry submit(
    @NotNull @Valid CreateInquiryRequest request,
    @NotNull List<MultipartFile> files
  ) {
    if (files.size() > MAX_ATTACHMENTS) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "A project inquiry may contain at most five attachments"
      );
    }

    var validatedFiles = files.stream()
      .map(validator::validate)
      .toList();

    var storedKeys = new ArrayList<String>();

    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status != STATUS_COMMITTED) {
            deleteStoredFiles(storedKeys);
          }
        }
      }
    );

    var inquiry = inquiries.createInquiry(request);

    for (var file : validatedFiles) {
      String storageKey;

      try {
        storageKey = storage.store(file.content());
      } catch (IOException exception) {
        throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Cannot store inquiry attachment",
          exception
        );
      }

      storedKeys.add(storageKey);

      attachments.save(new InquiryAttachment(
        inquiry.getId(),
        file.originalFilename(),
        storageKey,
        file.contentType(),
        file.content().length
      ));
    }

    attachments.flush();

    return inquiry;
  }

  private void deleteStoredFiles(List<String> storageKeys) {
    for (String storageKey : storageKeys) {
      try {
        storage.delete(storageKey);
      } catch (IOException exception) {
        LOGGER.error(
          "Cannot remove attachment after rolled-back submission: {}",
          storageKey,
          exception
        );
      }
    }
  }
}
