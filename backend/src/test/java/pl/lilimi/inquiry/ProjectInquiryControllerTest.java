package pl.lilimi.inquiry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class ProjectInquiryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProjectInquiryRepository inquiries;

  private String validRequest() {
    return """
      {
        "language": "pl",
        "name": "Anna Kowalska",
        "email": "anna@example.com",
        "projectType": "website",
        "description": "Potrzebuję strony dla swojej pracowni.",
        "inspirationUrl": "https://example.com/inspiration"
      }
      """;
  }

  @Test
  void shouldCreateInquiryWithoutLogin() throws Exception {
    long initialCount = inquiries.count();

    var result = mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content(validRequest())
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").isString())
      .andExpect(jsonPath("$.createdAt").isString())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.email").doesNotExist())
      .andExpect(jsonPath("$.description").doesNotExist())
      .andReturn();

    String id = JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString())
      .get("id")
      .asText();

    var saved = inquiries.findById(UUID.fromString(id)).orElseThrow();

    assertThat(inquiries.count()).isEqualTo(initialCount + 1);
    assertThat(saved.getName()).isEqualTo("Anna Kowalska");
    assertThat(saved.getEmail()).isEqualTo("anna@example.com");
    assertThat(saved.getProjectType()).isEqualTo("website");
    assertThat(saved.getDescription())
      .isEqualTo("Potrzebuję strony dla swojej pracowni.");
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
      post("/api/project-inquiries")
        .contentType("application/json")
        .content(validRequest())
    ).andExpect(status().isForbidden());

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectInvalidEmailWithoutSavingInquiry() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content(validRequest().replace(
            "anna@example.com",
            "invalid-email"
          ))
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INQUIRY_VALIDATION_FAILED"))
      .andExpect(jsonPath("$.fields[0]").value("email"));

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectUnavailableProductWithoutSavingInquiry() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content("""
          {
            "language": "pl",
            "name": "Anna Kowalska",
            "email": "anna@example.com",
            "projectType": "embroideredProduct",
            "description": "Proszę o wycenę personalizacji.",
            "productId": "missing-product"
          }
          """)
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("INQUIRY_PRODUCT_UNAVAILABLE"))
      .andExpect(jsonPath("$.productId").value("missing-product"));

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectUnreadableRequestWithoutSavingInquiry() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content("{ invalid json")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INQUIRY_REQUEST_INVALID"));

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }
}
