package pl.lilimi.inquiry;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class ProjectInquiryRepositoryTest {

  @Autowired
  private ProjectInquiryRepository inquiries;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSaveAndReadInquiryWithNormalizedContactDetails() {
    var saved = inquiries.saveAndFlush(
      new ProjectInquiry(
        "pl",
        "  Anna Kowalska  ",
        "  ANNA@EXAMPLE.COM  ",
        "website",
        "  Potrzebuję strony dla swojej pracowni.  ",
        "  https://example.com/inspiration  ",
        null
      )
    );

    var id = saved.getId();

    entityManager.clear();

    var loaded = inquiries.findById(id).orElseThrow();

    assertThat(loaded.getId()).isNotNull();
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getStatus()).isEqualTo("new");
    assertThat(loaded.getLanguage()).isEqualTo("pl");
    assertThat(loaded.getName()).isEqualTo("Anna Kowalska");
    assertThat(loaded.getEmail()).isEqualTo("anna@example.com");
    assertThat(loaded.getProjectType()).isEqualTo("website");
    assertThat(loaded.getDescription())
      .isEqualTo("Potrzebuję strony dla swojej pracowni.");
    assertThat(loaded.getInspirationUrl())
      .isEqualTo("https://example.com/inspiration");
    assertThat(loaded.getProductId()).isNull();
  }

  @Test
  void shouldSaveBlankOptionalFieldsAsNull() {
    var saved = inquiries.saveAndFlush(
      new ProjectInquiry(
        "en",
        "Anna Kowalska",
        "anna@example.com",
        "other",
        "I would like to discuss a project.",
        "   ",
        ""
      )
    );

    var id = saved.getId();

    entityManager.clear();

    var loaded = inquiries.findById(id).orElseThrow();

    assertThat(loaded.getInspirationUrl()).isNull();
    assertThat(loaded.getProductId()).isNull();
  }

  @Test
  void shouldSaveTheProductReference() {
    var saved = inquiries.saveAndFlush(
      new ProjectInquiry(
        "pl",
        "Anna Kowalska",
        "anna@example.com",
        "embroideredProduct",
        "Zapytanie o personalizację bluzy.",
        null,
        "  embroidered-002  "
      )
    );

    var id = saved.getId();

    entityManager.clear();

    var loaded = inquiries.findById(id).orElseThrow();

    assertThat(loaded.getProductId()).isEqualTo("embroidered-002");
    assertThat(loaded.getInspirationUrl()).isNull();
  }
}
