package pl.lilimi.inquiry;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminInquiryStatusControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProjectInquiryRepository inquiries;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldChangeStatusAndReturnUpdatedVersion() throws Exception {
    var inquiry = saveInquiry();
    var id = inquiry.getId();
    long originalVersion = inquiry.getVersion();

    mockMvc.perform(
        patch("/api/admin/project-inquiries/{id}/status", id)
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("in_progress", originalVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(id.toString()))
      .andExpect(jsonPath("$.status").value("in_progress"))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(originalVersion + 1)
      ));

    entityManager.clear();

    var saved = inquiries.findById(id).orElseThrow();

    assertThat(saved.getStatus()).isEqualTo("in_progress");
    assertThat(saved.getVersion()).isEqualTo(originalVersion + 1);
  }

  @Test
  void shouldRejectIncorrectVersionWithoutChangingStatus()
    throws Exception {
    var inquiry = saveInquiry();
    var id = inquiry.getId();
    long originalVersion = inquiry.getVersion();

    mockMvc.perform(
        patch("/api/admin/project-inquiries/{id}/status", id)
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("in_progress", originalVersion + 1))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "INQUIRY_VERSION_CONFLICT"
      ));

    entityManager.clear();

    var saved = inquiries.findById(id).orElseThrow();

    assertThat(saved.getStatus()).isEqualTo("new");
    assertThat(saved.getVersion()).isEqualTo(originalVersion);
  }

  @Test
  void shouldRejectInvalidTransitionWithoutChangingStatus()
    throws Exception {
    var inquiry = saveInquiry();
    var id = inquiry.getId();
    long originalVersion = inquiry.getVersion();

    mockMvc.perform(
        patch("/api/admin/project-inquiries/{id}/status", id)
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("answered", originalVersion))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "INQUIRY_STATUS_TRANSITION_INVALID"
      ));

    entityManager.clear();

    var saved = inquiries.findById(id).orElseThrow();

    assertThat(saved.getStatus()).isEqualTo("new");
    assertThat(saved.getVersion()).isEqualTo(originalVersion);
  }

  @Test
  void shouldReturnNotFoundForMissingInquiry() throws Exception {
    mockMvc.perform(
        patch(
          "/api/admin/project-inquiries/{id}/status",
          UUID.randomUUID()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("in_progress", 0))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    var inquiry = saveInquiry();

    mockMvc.perform(
        patch(
          "/api/admin/project-inquiries/{id}/status",
          inquiry.getId()
        )
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("in_progress", inquiry.getVersion()))
      )
      .andExpect(status().isUnauthorized());

    entityManager.clear();

    assertThat(
      inquiries.findById(inquiry.getId()).orElseThrow().getStatus()
    ).isEqualTo("new");
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    var inquiry = saveInquiry();

    mockMvc.perform(
        patch(
          "/api/admin/project-inquiries/{id}/status",
          inquiry.getId()
        )
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("in_progress", inquiry.getVersion()))
      )
      .andExpect(status().isForbidden());

    entityManager.clear();

    assertThat(
      inquiries.findById(inquiry.getId()).orElseThrow().getStatus()
    ).isEqualTo("new");
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    var inquiry = saveInquiry();

    mockMvc.perform(
        patch(
          "/api/admin/project-inquiries/{id}/status",
          inquiry.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("in_progress", inquiry.getVersion()))
      )
      .andExpect(status().isForbidden());

    entityManager.clear();

    assertThat(
      inquiries.findById(inquiry.getId()).orElseThrow().getStatus()
    ).isEqualTo("new");
  }

  @Test
  void shouldRejectUnknownStatus() throws Exception {
    var inquiry = saveInquiry();

    mockMvc.perform(
        patch(
          "/api/admin/project-inquiries/{id}/status",
          inquiry.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("unknown", inquiry.getVersion()))
      )
      .andExpect(status().isBadRequest());

    entityManager.clear();

    assertThat(
      inquiries.findById(inquiry.getId()).orElseThrow().getStatus()
    ).isEqualTo("new");
  }

  @Test
  void shouldRejectMissingVersion() throws Exception {
    var inquiry = saveInquiry();

    mockMvc.perform(
        patch(
          "/api/admin/project-inquiries/{id}/status",
          inquiry.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "status": "in_progress"
            }
            """)
      )
      .andExpect(status().isBadRequest());

    entityManager.clear();

    assertThat(
      inquiries.findById(inquiry.getId()).orElseThrow().getStatus()
    ).isEqualTo("new");
  }

  @Test
  void shouldRejectNegativeVersion() throws Exception {
    var inquiry = saveInquiry();

    mockMvc.perform(
        patch(
          "/api/admin/project-inquiries/{id}/status",
          inquiry.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody("in_progress", -1))
      )
      .andExpect(status().isBadRequest());

    entityManager.clear();

    assertThat(
      inquiries.findById(inquiry.getId()).orElseThrow().getStatus()
    ).isEqualTo("new");
  }

  private ProjectInquiry saveInquiry() {
    return inquiries.saveAndFlush(
      new ProjectInquiry(
        "pl",
        "Anna Kowalska",
        "anna@example.com",
        "website",
        "Potrzebuję strony dla pracowni.",
        null,
        null
      )
    );
  }

  private static String requestBody(
    String status,
    long expectedVersion
  ) {
    return """
      {
        "status": "%s",
        "expectedVersion": %d
      }
      """.formatted(status, expectedVersion);
  }
}
