package pl.lilimi.inquiry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.lilimi.inquiry.domain.ProjectInquiry;

import java.util.UUID;

public interface ProjectInquiryRepository
  extends JpaRepository<ProjectInquiry, UUID> {
}
