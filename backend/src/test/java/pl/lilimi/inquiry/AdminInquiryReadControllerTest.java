package pl.lilimi.inquiry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.InquiryReadRepository;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminInquiryReadControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private ProjectInquiryRepository inquiries;

  @Autowired
  private InquiryReadRepository reads;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldMarkReadForCurrentAdministratorWithoutChangingInquiry()
    throws Exception {

    var admin = saveAccount(UserRole.ADMIN);
    var anotherAdmin = saveAccount(UserRole.ADMIN);
    var inquiry = saveInquiry();
    String originalStatus = inquiry.getStatus();
    long originalVersion = inquiry.getVersion();

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(
          post(
            "/api/admin/project-inquiries/"
              + inquiry.getId()
              + "/read"
          )
            .with(user(admin.getEmail()).roles("ADMIN"))
            .with(csrf())
        )
        .andExpect(status().isNoContent());
    }

    assertThat(reads.findReadIds(
      admin.getId(),
      List.of(inquiry.getId())
    )).containsExactly(inquiry.getId());

    assertThat(reads.findReadIds(
      anotherAdmin.getId(),
      List.of(inquiry.getId())
    )).isEmpty();

    assertThat(jdbc.queryForObject(
      """
      SELECT COUNT(*) FROM admin_inquiry_reads
      WHERE administrator_id = ? AND inquiry_id = ?
      """,
      Long.class,
      admin.getId(),
      inquiry.getId()
    )).isEqualTo(1L);

    assertThat(jdbc.queryForObject(
      "SELECT status FROM project_inquiries WHERE id = ?",
      String.class,
      inquiry.getId()
    )).isEqualTo(originalStatus);

    assertThat(jdbc.queryForObject(
      "SELECT version FROM project_inquiries WHERE id = ?",
      Long.class,
      inquiry.getId()
    )).isEqualTo(originalVersion);
  }

  @Test
  void shouldReturnReadStateForCurrentAdministrator()
    throws Exception {

    var admin = saveAccount(UserRole.ADMIN);
    var anotherAdmin = saveAccount(UserRole.ADMIN);
    var inquiry = saveInquiry();

    // Fetching the list must not mark an inquiry as read.
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(
          get("/api/admin/project-inquiries")
            .param("view", "all")
            .param("search", inquiry.getId().toString())
            .with(user(admin.getEmail()).roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          inquiry.getId().toString()
        ))
        .andExpect(jsonPath("$.items[0].read").value(false));
    }

    assertThat(reads.findReadIds(
      admin.getId(),
      List.of(inquiry.getId())
    )).isEmpty();

    mockMvc.perform(
        post(
          "/api/admin/project-inquiries/"
            + inquiry.getId()
            + "/read"
        )
          .with(user(admin.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNoContent());

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("view", "all")
          .param("search", inquiry.getId().toString())
          .with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(true));

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("view", "all")
          .param("search", inquiry.getId().toString())
          .with(user(anotherAdmin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(false));
  }

  @Test
  void shouldKeepOtherInquiriesUnread() throws Exception {
    var admin = saveAccount(UserRole.ADMIN);
    var firstInquiry = saveInquiry();
    var secondInquiry = saveInquiry();

    mockMvc.perform(
        post(
          "/api/admin/project-inquiries/"
            + firstInquiry.getId()
            + "/read"
        )
          .with(user(admin.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNoContent());

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("view", "all")
          .param("search", firstInquiry.getId().toString())
          .with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(true));

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("view", "all")
          .param("search", secondInquiry.getId().toString())
          .with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(false));
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        post(
          "/api/admin/project-inquiries/"
            + UUID.randomUUID()
            + "/read"
        ).with(csrf())
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    var customer = saveAccount(UserRole.CUSTOMER);

    mockMvc.perform(
        post(
          "/api/admin/project-inquiries/"
            + UUID.randomUUID()
            + "/read"
        )
          .with(user(customer.getEmail()).roles("CUSTOMER"))
          .with(csrf())
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldRequireCsrfWithoutSavingReadReceipt() throws Exception {
    var admin = saveAccount(UserRole.ADMIN);
    var inquiry = saveInquiry();

    mockMvc.perform(
        post(
          "/api/admin/project-inquiries/"
            + inquiry.getId()
            + "/read"
        ).with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isForbidden());

    assertThat(reads.findReadIds(
      admin.getId(),
      List.of(inquiry.getId())
    )).isEmpty();
  }

  @Test
  void shouldReturnNotFoundWithoutSavingReadReceipt() throws Exception {
    var admin = saveAccount(UserRole.ADMIN);
    UUID missingId = UUID.randomUUID();

    mockMvc.perform(
        post(
          "/api/admin/project-inquiries/"
            + missingId
            + "/read"
        )
          .with(user(admin.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNotFound());

    assertThat(reads.findReadIds(admin.getId(), List.of(missingId)))
      .isEmpty();
  }

  @Test
  void shouldCheckAdministratorRoleInDatabase() throws Exception {
    var customer = saveAccount(UserRole.CUSTOMER);
    var inquiry = saveInquiry();

    mockMvc.perform(
        post(
          "/api/admin/project-inquiries/"
            + inquiry.getId()
            + "/read"
        )
          .with(user(customer.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isForbidden());

    assertThat(reads.findReadIds(
      customer.getId(),
      List.of(inquiry.getId())
    )).isEmpty();
  }

  private UserAccount saveAccount(UserRole role) {
    return accounts.saveAndFlush(
      new UserAccount(
        UUID.randomUUID() + "@example.com",
        "test-password-hash",
        role
      )
    );
  }

  private ProjectInquiry saveInquiry() {
    return inquiries.saveAndFlush(
      new ProjectInquiry(
        "pl",
        "Anna Kowalska",
        "anna@example.com",
        "website",
        "Proszę o wycenę strony.",
        null,
        null
      )
    );
  }
}
