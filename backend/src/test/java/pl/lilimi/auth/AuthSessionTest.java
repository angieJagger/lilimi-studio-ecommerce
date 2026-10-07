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
