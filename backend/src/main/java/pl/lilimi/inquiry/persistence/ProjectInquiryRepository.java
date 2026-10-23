package pl.lilimi.inquiry.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.lilimi.inquiry.domain.ProjectInquiry;

import java.util.UUID;

public interface ProjectInquiryRepository
  extends JpaRepository<ProjectInquiry, UUID>,
  JpaSpecificationExecutor<ProjectInquiry> {
}
