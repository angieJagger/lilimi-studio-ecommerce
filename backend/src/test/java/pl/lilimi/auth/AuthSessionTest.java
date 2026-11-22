package pl.lilimi.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.customer.domain.CustomerProfile;
import pl.lilimi.customer.persistence.CustomerProfileRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
  @Autowired
  private CustomerProfileRepository profileRepository;

  @Test
  void shouldRequireMfaAfterPasswordLoginAndAllowLogout() throws Exception {
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
      .andExpect(status().isAccepted())
      .andReturn();

    var session = loginResult.getResponse().getCookie("JSESSIONID");
    assertThat(session).isNotNull();

    mockMvc.perform(get("/api/auth/me").cookie(session))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));

    mockMvc.perform(get("/api/admin/auth-check").cookie(session))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));

    mockMvc.perform(get("/api/auth/session").cookie(session))
      .andExpect(status().isNoContent())
      .andExpect(header().string("X-MFA-Required", "true"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(content().string(""));

    var logoutCsrf = getCsrfCookie(session);

    mockMvc.perform(post("/api/auth/logout")
        .cookie(session, logoutCsrf)
        .header("X-XSRF-TOKEN", logoutCsrf.getValue()))
      .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/auth/session").cookie(session))
      .andExpect(status().isNoContent())
      .andExpect(header().doesNotExist("X-MFA-Required"));

    mockMvc.perform(get("/api/admin/auth-check"))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnNoContentForGuestSession() throws Exception {
    mockMvc.perform(get("/api/auth/session"))
      .andExpect(status().isNoContent())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(content().string(""));
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

    var session = result.getResponse().getCookie("JSESSIONID");

    var adminRequest = get("/api/admin/auth-check");

    if (session != null) {
      adminRequest.cookie(session);
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

    var firstSession = firstLogin.getResponse().getCookie("JSESSIONID");

    var secondCsrf = getCsrfCookie(null);

    var secondLogin = mockMvc.perform(post("/api/auth/login")
        .cookie(secondCsrf)
        .header("X-XSRF-TOKEN", secondCsrf.getValue())
        .param("email", email)
        .param("password", oldPassword))
      .andExpect(status().isNoContent())
      .andReturn();

    var secondSession = secondLogin.getResponse().getCookie("JSESSIONID");

    assertThat(firstSession).isNotNull();
    assertThat(secondSession).isNotNull();

    mockMvc.perform(get("/api/auth/me").cookie(firstSession))
      .andExpect(status().isOk());

    mockMvc.perform(get("/api/auth/me").cookie(secondSession))
      .andExpect(status().isOk());

    account.changePasswordHash(passwordEncoder.encode(newPassword));
    accountRepository.saveAndFlush(account);

    assertThat(account.getCredentialsVersion()).isEqualTo(1);

    mockMvc.perform(get("/api/auth/session").cookie(firstSession))
      .andExpect(status().isNoContent())
      .andExpect(content().string(""));

    mockMvc.perform(get("/api/auth/me").cookie(secondSession))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/auth/me").cookie(firstSession))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/auth/me").cookie(secondSession))
      .andExpect(status().isUnauthorized());

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

    var newSession = newLogin.getResponse().getCookie("JSESSIONID");

    assertThat(newSession).isNotNull();

    mockMvc.perform(get("/api/auth/me").cookie(newSession))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(email))
      .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
  }

  @Test
  void shouldReturnCustomerFirstName() throws Exception {
    var account = accountRepository.saveAndFlush(new UserAccount(
      "named-customer@example.com",
      passwordEncoder.encode("Test-only-password-2026!"),
      UserRole.CUSTOMER
    ));

    var profile = new CustomerProfile(account.getId());
    profile.updateContact("Anna", "Kowalska", null);
    profileRepository.saveAndFlush(profile);

    mockMvc.perform(get("/api/auth/me")
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Anna"))
      .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
      .andExpect(jsonPath("$.lastName").doesNotExist());
  }

  @Test
  void shouldReturnCustomerWithoutProfile() throws Exception {
    var account = accountRepository.saveAndFlush(new UserAccount(
      "customer-without-profile@example.com",
      passwordEncoder.encode("Test-only-password-2026!"),
      UserRole.CUSTOMER
    ));

    mockMvc.perform(get("/api/auth/me")
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(account.getEmail()))
      .andExpect(jsonPath("$.firstName").doesNotExist());
  }

  private Cookie getCsrfCookie(Cookie session)
    throws Exception {

    var request = get("/api/auth/csrf");

    if (session != null) {
      request.cookie(session);
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
