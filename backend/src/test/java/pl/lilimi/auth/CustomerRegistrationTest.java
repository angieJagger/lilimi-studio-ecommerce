package pl.lilimi.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class CustomerRegistrationTest {

  private static final String EMAIL = "registration-test@example.com";
  private static final String PASSWORD = "Customer-test-password!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void shouldRegisterCustomerAndAllowLoginWithoutAdminAccess() throws Exception {
    var csrf = getCsrfCookie();

    var registration = mockMvc.perform(post("/api/auth/register")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson("Registration-Test@example.com", PASSWORD)))
      .andExpect(status().isCreated())
      .andReturn();

    assertThat(registration.getResponse().getContentAsString()).isEmpty();

    var account = accounts.findByEmail(EMAIL).orElseThrow();

    assertThat(account.getRole()).isEqualTo(UserRole.CUSTOMER);
    assertThat(account.isEnabled()).isTrue();
    assertThat(account.getPasswordHash()).isNotEqualTo(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, account.getPasswordHash()))
      .isTrue();

    mockMvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized());

    var loginCsrf = getCsrfCookie();

    var login = mockMvc.perform(post("/api/auth/login")
        .cookie(loginCsrf)
        .header("X-XSRF-TOKEN", loginCsrf.getValue())
        .param("email", EMAIL)
        .param("password", PASSWORD))
      .andExpect(status().isNoContent())
      .andReturn();

    var session = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(session).isNotNull();

    mockMvc.perform(get("/api/auth/me").session(session))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(EMAIL))
      .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
      .andExpect(jsonPath("$.password").doesNotExist())
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    mockMvc.perform(get("/api/admin/products").session(session))
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectRegistrationWithoutCsrf() throws Exception {
    long count = accounts.count();

    mockMvc.perform(post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson(EMAIL, PASSWORD)))
      .andExpect(status().isForbidden());

    assertThat(accounts.count()).isEqualTo(count);
  }

  @Test
  void shouldRejectDuplicateEmailWithoutChangingExistingAccount() throws Exception {
    var original = accounts.saveAndFlush(new UserAccount(
      EMAIL,
      passwordEncoder.encode(PASSWORD),
      UserRole.ADMIN
    ));

    String originalHash = original.getPasswordHash();
    long count = accounts.count();
    var csrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/register")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson("Registration-Test@example.com", "Different-password!")))
      .andExpect(status().isConflict());

    assertThat(accounts.count()).isEqualTo(count);

    var unchanged = accounts.findByEmail(EMAIL).orElseThrow();
    assertThat(unchanged.getId()).isEqualTo(original.getId());
    assertThat(unchanged.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(unchanged.getPasswordHash()).isEqualTo(originalHash);
  }

  @Test
  void shouldRejectInvalidEmail() throws Exception {
    assertInvalidRequest("not-an-email", PASSWORD);
  }

  @Test
  void shouldRejectShortPassword() throws Exception {
    assertInvalidRequest(EMAIL, "short");
  }

  @Test
  void shouldRejectPasswordExceedingBcryptByteLimit() throws Exception {
    assertInvalidRequest(EMAIL, "ą".repeat(37));
  }

  private void assertInvalidRequest(String email, String password)
    throws Exception {

    long count = accounts.count();
    var csrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/register")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson(email, password)))
      .andExpect(status().isBadRequest());

    assertThat(accounts.count()).isEqualTo(count);
  }

  private Cookie getCsrfCookie() throws Exception {
    var result = mockMvc.perform(get("/api/auth/csrf"))
      .andExpect(status().isNoContent())
      .andReturn();

    var cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private String requestJson(String email, String password) {
    return """
      {
        "email": "%s",
        "password": "%s"
      }
      """.formatted(email, password);
  }
}
