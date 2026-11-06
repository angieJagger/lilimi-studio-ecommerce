package pl.lilimi.inquiry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lilimi.inquiry.domain.InquiryAttachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryAttachmentRepository
  extends JpaRepository<InquiryAttachment, UUID> {

  List<InquiryAttachment> findAllByInquiryIdOrderByCreatedAtAscIdAsc(
    UUID inquiryId
  );

  Optional<InquiryAttachment> findByIdAndInquiryId(
    UUID id,
    UUID inquiryId
  );

  boolean existsByStorageKey(String storageKey);
}
