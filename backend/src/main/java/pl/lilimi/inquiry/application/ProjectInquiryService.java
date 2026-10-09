package pl.lilimi.inquiry.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.inquiry.api.CreateInquiryRequest;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

@Service
@Validated
public class ProjectInquiryService {

  private final ProjectInquiryRepository inquiries;
  private final ProductRepository products;

  public ProjectInquiryService(
    ProjectInquiryRepository inquiries,
    ProductRepository products
  ) {
    this.inquiries = inquiries;
    this.products = products;
  }

  @Transactional
  public ProjectInquiry createInquiry(
    @NotNull @Valid CreateInquiryRequest request
  ) {
    String productId = request.productId();

    if (productId != null && !productId.isBlank()) {
      productId = productId.trim();

      products.findByIdAndActiveTrue(productId)
        .orElseThrow(() ->
          new InquiryProductUnavailableException(
            request.productId().trim()
          )
        );
    }

    var inquiry = new ProjectInquiry(
      request.language(),
      request.name(),
      request.email(),
      request.projectType(),
      request.description(),
      request.inspirationUrl(),
      productId
    );

    return inquiries.saveAndFlush(inquiry);
  }
}
