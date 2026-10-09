package pl.lilimi.inquiry;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.api.CreateInquiryRequest;
import pl.lilimi.inquiry.application.InquiryProductUnavailableException;
import pl.lilimi.inquiry.application.ProjectInquiryService;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class ProjectInquiryServiceTest {

  @Autowired
  private ProjectInquiryService service;

  @Autowired
  private ProjectInquiryRepository inquiries;

  private CreateInquiryRequest request(
    String description,
    String inspirationUrl,
    String productId
  ) {
    return new CreateInquiryRequest(
      "pl",
      "  Anna Kowalska  ",
      "ANNA@EXAMPLE.COM",
      "website",
      description,
      inspirationUrl,
      productId
    );
  }

  @Test
  void shouldSaveInquiryWithNormalizedDetails() {
    long initialCount = inquiries.count();

    var created = service.createInquiry(
      request(
        "  Potrzebuję strony dla swojej pracowni.  ",
        "https://example.com/inspiration",
        null
      )
    );

    var saved = inquiries.findById(created.getId()).orElseThrow();

    assertThat(inquiries.count()).isEqualTo(initialCount + 1);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo("new");
    assertThat(saved.getName()).isEqualTo("Anna Kowalska");
    assertThat(saved.getEmail()).isEqualTo("anna@example.com");
    assertThat(saved.getDescription())
      .isEqualTo("Potrzebuję strony dla swojej pracowni.");
    assertThat(saved.getInspirationUrl())
      .isEqualTo("https://example.com/inspiration");
    assertThat(saved.getProductId()).isNull();
  }

  @Test
  void shouldAcceptAnActiveProductReference() {
    var created = service.createInquiry(
      new CreateInquiryRequest(
        "pl",
        "Anna Kowalska",
        "anna@example.com",
        "embroideredProduct",
        "Proszę o wycenę personalizacji bluzy.",
        null,
        "  embroidered-002  "
      )
    );

    assertThat(created.getProductId()).isEqualTo("embroidered-002");
  }

  @Test
  void shouldRejectMissingProductWithoutSavingInquiry() {
    long initialCount = inquiries.count();

    assertThatThrownBy(() ->
      service.createInquiry(
        request("Opis projektu.", null, "missing-product")
      )
    )
      .isInstanceOf(InquiryProductUnavailableException.class);

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectBlankDescriptionWithoutSavingInquiry() {
    long initialCount = inquiries.count();

    assertThatThrownBy(() ->
      service.createInquiry(request("   ", null, null))
    )
      .isInstanceOf(ConstraintViolationException.class);

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectInvalidInspirationUrlsWithoutSavingInquiry() {
    long initialCount = inquiries.count();

    String[] invalidUrls = {
      "ftp://example.com/file",
      "https://",
      "https://user:password@example.com",
      "https://bad host.example"
    };

    for (String url : invalidUrls) {
      assertThatThrownBy(() ->
        service.createInquiry(request("Opis projektu.", url, null))
      )
        .isInstanceOf(ConstraintViolationException.class);
    }

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }
}
