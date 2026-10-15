package pl.lilimi.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  PostgresTestConfiguration.class,
  AuthSessionTest.AdminProbeController.class
})
@Transactional
class AuthSessionTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accountRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void shouldLogInAccessAdminEndpointAndLogOut() throws Exception {
    accountRepository.saveAndFlush(new UserAccount(
      "admin-test@example.com",
      passwordEncoder.encode("Test-only-password-2026!"),
      UserRole.ADMIN
    ));

    var loginCsrf = getCsrfCookie(null);

    var loginResult = mockMvc.perform(post("/api/auth/login")
        .cookie(loginCsrf)
        .header("X-XSRF-TOKEN", loginCsrf.getValue())
        .param("email", "admin-test@example.com")
        .param("password", "Test-only-password-2026!"))
      .andExpect(status().isNoContent())
      .andReturn();

    var session = (MockHttpSession) loginResult
      .getRequest()
      .getSession(false);

    assertThat(session).isNotNull();

    mockMvc.perform(get("/api/auth/me")
        .session(session))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value("admin-test@example.com"))
      .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
      .andExpect(jsonPath("$.password").doesNotExist())
      .andExpect(jsonPath("$.passwordHash").doesNotExist());

    mockMvc.perform(get("/api/admin/auth-check")
        .session(session))
      .andExpect(status().isOk())
      .andExpect(content().string("ok"));

    var logoutCsrf = getCsrfCookie(session);

    mockMvc.perform(post("/api/auth/logout")
        .session(session)
        .cookie(logoutCsrf)
        .header("X-XSRF-TOKEN", logoutCsrf.getValue()))
      .andExpect(status().isNoContent());

    assertThat(session.isInvalid()).isTrue();

    mockMvc.perform(get("/api/admin/auth-check"))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectIncorrectPassword() throws Exception {
    accountRepository.saveAndFlush(new UserAccount(
      "admin-test@example.com",
      passwordEncoder.encode("Test-only-password-2026!"),
      UserRole.ADMIN
    ));

    var csrfCookie = getCsrfCookie(null);

    var result = mockMvc.perform(post("/api/auth/login")
        .cookie(csrfCookie)
        .header("X-XSRF-TOKEN", csrfCookie.getValue())
        .param("email", "admin-test@example.com")
        .param("password", "Incorrect-password"))
      .andExpect(status().isUnauthorized())
      .andReturn();

    var session = (MockHttpSession) result
      .getRequest()
      .getSession(false);

    var adminRequest = get("/api/admin/auth-check");

    if (session != null) {
      adminRequest.session(session);
    }

    mockMvc.perform(adminRequest)
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRequireLoginToReadCurrentUser() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldInvalidateExistingSessionsAfterPasswordChange() throws Exception {
    String email = "password-change@example.com";
    String oldPassword = "Original-password-2026!";
    String newPassword = "Changed-password-2026!";

    var account = accountRepository.saveAndFlush(new UserAccount(
      email,
      passwordEncoder.encode(oldPassword),
      UserRole.CUSTOMER
    ));

    var firstCsrf = getCsrfCookie(null);

    var firstLogin = mockMvc.perform(post("/api/auth/login")
        .cookie(firstCsrf)
        .header("X-XSRF-TOKEN", firstCsrf.getValue())
        .param("email", email)
        .param("password", oldPassword))
      .andExpect(status().isNoContent())
      .andReturn();

    var firstSession = (MockHttpSession) firstLogin
      .getRequest().getSession(false);

    var secondCsrf = getCsrfCookie(null);

    var secondLogin = mockMvc.perform(post("/api/auth/login")
        .cookie(secondCsrf)
        .header("X-XSRF-TOKEN", secondCsrf.getValue())
        .param("email", email)
        .param("password", oldPassword))
      .andExpect(status().isNoContent())
      .andReturn();

    var secondSession = (MockHttpSession) secondLogin
      .getRequest().getSession(false);

    assertThat(firstSession).isNotNull();
    assertThat(secondSession).isNotNull();

    mockMvc.perform(get("/api/auth/me").session(firstSession))
      .andExpect(status().isOk());

    mockMvc.perform(get("/api/auth/me").session(secondSession))
      .andExpect(status().isOk());

    account.changePasswordHash(passwordEncoder.encode(newPassword));
    accountRepository.saveAndFlush(account);

    assertThat(account.getCredentialsVersion()).isEqualTo(1);

    mockMvc.perform(get("/api/auth/me").session(firstSession))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/auth/me").session(secondSession))
      .andExpect(status().isUnauthorized());

    assertThat(firstSession.isInvalid()).isTrue();
    assertThat(secondSession.isInvalid()).isTrue();

    var oldLoginCsrf = getCsrfCookie(null);

    mockMvc.perform(post("/api/auth/login")
        .cookie(oldLoginCsrf)
        .header("X-XSRF-TOKEN", oldLoginCsrf.getValue())
        .param("email", email)
        .param("password", oldPassword))
      .andExpect(status().isUnauthorized());

    var newLoginCsrf = getCsrfCookie(null);

    var newLogin = mockMvc.perform(post("/api/auth/login")
        .cookie(newLoginCsrf)
        .header("X-XSRF-TOKEN", newLoginCsrf.getValue())
        .param("email", email)
        .param("password", newPassword))
      .andExpect(status().isNoContent())
      .andReturn();

    var newSession = (MockHttpSession) newLogin
      .getRequest().getSession(false);

    assertThat(newSession).isNotNull();

    mockMvc.perform(get("/api/auth/me").session(newSession))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(email))
      .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
  }

  private Cookie getCsrfCookie(MockHttpSession session)
    throws Exception {

    var request = get("/api/auth/csrf");

    if (session != null) {
      request.session(session);
    }

    var result = mockMvc.perform(request)
      .andExpect(status().isNoContent())
      .andExpect(cookie().exists("XSRF-TOKEN"))
      .andReturn();

    var csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");

    assertThat(csrfCookie).isNotNull();

    return csrfCookie;
  }

  @RestController
  public static class AdminProbeController {

    @GetMapping("/api/admin/auth-check")
    public String check() {
      return "ok";
    }
  }
}
