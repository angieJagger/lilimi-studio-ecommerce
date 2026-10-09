package pl.lilimi.inquiry;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import java.sql.Timestamp;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminInquiryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProjectInquiryRepository inquiries;

  @Autowired
  private JdbcTemplate jdbc;

  private ProjectInquiry saveInquiry(
    String email,
    Instant createdAt
  ) {
    var inquiry = inquiries.saveAndFlush(
      new ProjectInquiry(
        "pl",
        "Anna Kowalska",
        email,
        "website",
        "Potrzebuję strony dla swojej pracowni.",
        null,
        null
      )
    );

    jdbc.update(
      "UPDATE project_inquiries SET created_at = ? WHERE id = ?",
      Timestamp.from(createdAt),
      inquiry.getId()
    );

    return inquiry;
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
      get("/api/admin/project-inquiries")
    ).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
      get("/api/admin/project-inquiries")
        .with(user("customer@example.com").roles("CUSTOMER"))
    ).andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowAdministratorWithDefaultPagination() throws Exception {
    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.items").isArray());
  }

  @Test
  void shouldReturnNewestInquiriesFirstWithPagination() throws Exception {
    long initialCount = inquiries.count();

    var older = saveInquiry(
      "older@example.com",
      Instant.parse("2090-01-01T10:00:00Z")
    );

    var newer = saveInquiry(
      "newer@example.com",
      Instant.parse("2090-01-02T10:00:00Z")
    );

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("page", "0")
          .param("size", "1")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].id").value(
        newer.getId().toString()
      ))
      .andExpect(jsonPath("$.items[0].status").value("new"))
      .andExpect(jsonPath("$.items[0].name").value("Anna Kowalska"))
      .andExpect(jsonPath("$.items[0].email").value("newer@example.com"))
      .andExpect(jsonPath("$.items[0].projectType").value("website"))
      .andExpect(jsonPath("$.totalElements").value(
        (int) (initialCount + 2)
      ))
      .andExpect(jsonPath("$.totalPages").value(
        (int) (initialCount + 2)
      ));

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("page", "1")
          .param("size", "1")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(1))
      .andExpect(jsonPath("$.items[0].id").value(
        older.getId().toString()
      ));
  }

  @Test
  void shouldRejectInvalidPagination() throws Exception {
    for (String query : new String[] {
      "?page=-1",
      "?size=0",
      "?size=101"
    }) {
      mockMvc.perform(
        get("/api/admin/project-inquiries" + query)
          .with(user("admin@example.com").roles("ADMIN"))
      ).andExpect(status().isBadRequest());
    }
  }

  @Test
  void shouldReturnFullInquiryDetails() throws Exception {
    var inquiry = inquiries.saveAndFlush(
      new ProjectInquiry(
        "en",
        "Anna Kowalska",
        "anna@example.com",
        "embroideredProduct",
        "Please quote a personalized sweatshirt.",
        "https://example.com/inspiration",
        "embroidered-002"
      )
    );

    mockMvc.perform(
        get("/api/admin/project-inquiries/" + inquiry.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(inquiry.getId().toString()))
      .andExpect(jsonPath("$.createdAt").isString())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.language").value("en"))
      .andExpect(jsonPath("$.name").value("Anna Kowalska"))
      .andExpect(jsonPath("$.email").value("anna@example.com"))
      .andExpect(jsonPath("$.projectType").value("embroideredProduct"))
      .andExpect(jsonPath("$.description").value(
        "Please quote a personalized sweatshirt."
      ))
      .andExpect(jsonPath("$.inspirationUrl").value(
        "https://example.com/inspiration"
      ))
      .andExpect(jsonPath("$.productId").value("embroidered-002"));
  }

  @Test
  void shouldRejectAnonymousUserReadingInquiryDetails() throws Exception {
    mockMvc.perform(
      get("/api/admin/project-inquiries/" + UUID.randomUUID())
    ).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomerReadingInquiryDetails() throws Exception {
    mockMvc.perform(
      get("/api/admin/project-inquiries/" + UUID.randomUUID())
        .with(user("customer@example.com").roles("CUSTOMER"))
    ).andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnNotFoundForMissingInquiry() throws Exception {
    mockMvc.perform(
      get("/api/admin/project-inquiries/" + UUID.randomUUID())
        .with(user("admin@example.com").roles("ADMIN"))
    ).andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectInvalidInquiryId() throws Exception {
    mockMvc.perform(
      get("/api/admin/project-inquiries/invalid-id")
        .with(user("admin@example.com").roles("ADMIN"))
    ).andExpect(status().isBadRequest());
  }
}
