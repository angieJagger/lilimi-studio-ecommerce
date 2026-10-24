package pl.lilimi.inquiry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import pl.lilimi.inquiry.domain.InquiryStatus;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

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
  @Autowired
  private UserAccountRepository accounts;

  private ProjectInquiry saveInquiry(
    String email,
    Instant createdAt
  ) {
    return saveInquiry(email, createdAt, "website");
  }

  @BeforeEach
  void ensureAdministratorExists() {
    if (accounts.findByEmail("admin@example.com").isEmpty()) {
      accounts.saveAndFlush(
        new UserAccount(
          "admin@example.com",
          "test-password-hash",
          UserRole.ADMIN
        )
      );
    }
  }

  private ProjectInquiry saveInquiry(
    String email,
    Instant createdAt,
    String projectType
  ) {
    var inquiry = inquiries.saveAndFlush(
      new ProjectInquiry(
        "pl",
        "Anna Kowalska",
        email,
        projectType,
        "Proszę o wycenę projektu.",
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
  void shouldSeparateInquiryViews() throws Exception {
    var existing = inquiries.findAll();

    Instant createdAt = Instant.parse("2091-01-10T12:00:00Z");

    var fresh = saveInquiry("new-view@example.com", createdAt);
    var processing = saveInquiry("progress-view@example.com", createdAt);
    var answered = saveInquiry("answered-view@example.com", createdAt);
    var closed = saveInquiry("closed-view@example.com", createdAt);

    processing.changeStatus(InquiryStatus.IN_PROGRESS);
    answered.changeStatus(InquiryStatus.IN_PROGRESS);
    answered.changeStatus(InquiryStatus.ANSWERED);
    closed.changeStatus(InquiryStatus.CLOSED);
    inquiries.flush();

    for (String view : new String[] {
      "active", "answered", "archive", "all"
    }) {
      long initialCount = existing.stream()
        .filter(inquiry -> switch (view) {
          case "active" ->
            "new".equals(inquiry.getStatus())
              || "in_progress".equals(inquiry.getStatus());
          case "answered" -> "answered".equals(inquiry.getStatus());
          case "archive" -> "closed".equals(inquiry.getStatus());
          default -> true;
        })
        .count();

      int addedCount = switch (view) {
        case "active" -> 2;
        case "all" -> 4;
        default -> 1;
      };

      var response = mockMvc.perform(
          get("/api/admin/project-inquiries")
            .param("view", view)
            .param("size", "100")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(
          (int) initialCount + addedCount
        ));

      if ("active".equals(view) || "all".equals(view)) {
        response
          .andExpect(jsonPath(
            "$.items[?(@.id == '" + fresh.getId() + "')]"
          ).isNotEmpty())
          .andExpect(jsonPath(
            "$.items[?(@.id == '" + processing.getId() + "')]"
          ).isNotEmpty());
      }

      if ("answered".equals(view) || "all".equals(view)) {
        response.andExpect(jsonPath(
          "$.items[?(@.id == '" + answered.getId() + "')]"
        ).isNotEmpty());
      }

      if ("archive".equals(view) || "all".equals(view)) {
        response.andExpect(jsonPath(
          "$.items[?(@.id == '" + closed.getId() + "')]"
        ).isNotEmpty());
      }
    }
  }

  @Test
  void shouldCombineProjectTypeAndStatusBeforePagination()
    throws Exception {

    long initialCount = inquiries.findAll().stream()
      .filter(inquiry ->
        "graphicDesign".equals(inquiry.getProjectType())
          && "new".equals(inquiry.getStatus())
      )
      .count();

    var older = saveInquiry(
      "graphic-older@example.com",
      Instant.parse("2091-02-10T09:00:00Z"),
      "graphicDesign"
    );

    var newer = saveInquiry(
      "graphic-newer@example.com",
      Instant.parse("2091-02-10T12:00:00Z"),
      "graphicDesign"
    );

    var excluded = saveInquiry(
      "graphic-closed@example.com",
      Instant.parse("2091-02-10T15:00:00Z"),
      "graphicDesign"
    );
    excluded.changeStatus(InquiryStatus.CLOSED);
    inquiries.flush();

    saveInquiry(
      "website-excluded@example.com",
      Instant.parse("2091-02-10T16:00:00Z"),
      "website"
    );

    String[] expectedIds = {
      newer.getId().toString(),
      older.getId().toString()
    };

    for (int page = 0; page < expectedIds.length; page++) {
      mockMvc.perform(
          get("/api/admin/project-inquiries")
            .param("view", "all")
            .param("status", "new")
            .param("projectType", "graphicDesign")
            .param("page", Integer.toString(page))
            .param("size", "1")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(expectedIds[page]))
        .andExpect(jsonPath("$.totalElements").value(
          (int) initialCount + 2
        ))
        .andExpect(jsonPath("$.totalPages").value(
          (int) initialCount + 2
        ));
    }
  }

  @Test
  void shouldReturnEmptyPageForStatusOutsideSelectedView()
    throws Exception {

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("view", "active")
          .param("status", "closed")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isEmpty())
      .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void shouldSearchByReferenceEmailAndCustomerName()
    throws Exception {

    var inquiry = saveInquiry(
      "search-client@example.com",
      Instant.parse("2092-01-10T12:00:00Z"),
      "digitizing"
    );

    for (String phrase : new String[] {
      inquiry.getId().toString(),
      inquiry.getId().toString().substring(0, 8),
      "SEARCH-CLIENT@EXAMPLE.COM",
      "client@",
      "KOWALSKA",
      "  Anna Kowalska  "
    }) {
      mockMvc.perform(
          get("/api/admin/project-inquiries")
            .param("view", "all")
            .param("projectType", "digitizing")
            .param("dateFrom", "2092-01-10")
            .param("dateTo", "2092-01-10")
            .param("search", phrase)
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          inquiry.getId().toString()
        ));
    }
  }

  @Test
  void shouldRespectWarsawBoundariesOnTwentyFiveHourDay()
    throws Exception {

    saveInquiry(
      "before-day@example.com",
      Instant.parse("2026-10-24T21:59:59.999999Z")
    );

    var first = saveInquiry(
      "day-start@example.com",
      Instant.parse("2026-10-24T22:00:00Z")
    );

    var last = saveInquiry(
      "day-end@example.com",
      Instant.parse("2026-10-25T22:59:59.999999Z")
    );

    saveInquiry(
      "after-day@example.com",
      Instant.parse("2026-10-25T23:00:00Z")
    );

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("dateFrom", "2026-10-25")
          .param("dateTo", "2026-10-25")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(2))
      .andExpect(jsonPath("$.items[0].id").value(
        last.getId().toString()
      ))
      .andExpect(jsonPath("$.items[1].id").value(
        first.getId().toString()
      ));
  }

  @Test
  void shouldCombineSearchDatesAndServiceBeforePagination()
    throws Exception {

    var older = saveInquiry(
      "quote-search-older@example.com",
      Instant.parse("2092-02-10T09:00:00Z"),
      "website"
    );

    var newer = saveInquiry(
      "quote-search-newer@example.com",
      Instant.parse("2092-02-10T12:00:00Z"),
      "website"
    );

    saveInquiry(
      "quote-search-other-service@example.com",
      Instant.parse("2092-02-10T15:00:00Z"),
      "digitizing"
    );

    saveInquiry(
      "quote-search-outside@example.com",
      Instant.parse("2092-02-11T12:00:00Z"),
      "website"
    );

    saveInquiry(
      "unrelated@example.com",
      Instant.parse("2092-02-10T16:00:00Z"),
      "website"
    );

    String[] expectedIds = {
      newer.getId().toString(),
      older.getId().toString()
    };

    for (int page = 0; page < expectedIds.length; page++) {
      mockMvc.perform(
          get("/api/admin/project-inquiries")
            .param("view", "active")
            .param("status", "new")
            .param("projectType", "website")
            .param("search", "quote-search")
            .param("dateFrom", "2092-02-10")
            .param("dateTo", "2092-02-10")
            .param("page", Integer.toString(page))
            .param("size", "1")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(expectedIds[page]))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));
    }
  }

  @Test
  void shouldTreatSearchWildcardsAsLiteralText()
    throws Exception {

    var literal = saveInquiry(
      "literal%_!@example.com",
      Instant.parse("2092-03-10T12:00:00Z")
    );

    saveInquiry(
      "ordinary@example.com",
      Instant.parse("2092-03-10T13:00:00Z")
    );

    for (String phrase : new String[] { "%", "_", "!", "%_!" }) {
      mockMvc.perform(
          get("/api/admin/project-inquiries")
            .param("search", phrase)
            .param("dateFrom", "2092-03-10")
            .param("dateTo", "2092-03-10")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          literal.getId().toString()
        ));
    }

    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("search", "' OR 1=1 --")
          .param("dateFrom", "2092-03-10")
          .param("dateTo", "2092-03-10")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isEmpty())
      .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void shouldIgnoreBlankSearch() throws Exception {
    var inquiry = saveInquiry(
      "blank-search@example.com",
      Instant.parse("2092-04-10T12:00:00Z")
    );

    for (String phrase : new String[] { "", "   " }) {
      mockMvc.perform(
          get("/api/admin/project-inquiries")
            .param("search", phrase)
            .param("dateFrom", "2092-04-10")
            .param("dateTo", "2092-04-10")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          inquiry.getId().toString()
        ));
    }
  }

  @Test
  void shouldRejectSearchLongerThanLimit() throws Exception {
    mockMvc.perform(
        get("/api/admin/project-inquiries")
          .param("search", "a".repeat(255))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectInvalidInquiryFilters() throws Exception {
    for (String query : new String[] {
      "?view=unknown",
      "?status=unknown",
      "?status=",
      "?projectType=unknown",
      "?projectType=",
      "?dateFrom=invalid",
      "?dateTo=2026-02-30",
      "?dateFrom=2026-10-22&dateTo=2026-10-21"
    }) {
      mockMvc.perform(
          get("/api/admin/project-inquiries" + query)
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isBadRequest());
    }
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
