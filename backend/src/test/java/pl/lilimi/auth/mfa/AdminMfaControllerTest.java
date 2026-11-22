package pl.lilimi.auth.mfa;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminMfaControllerTest {

  private static final String PASSWORD = "Test-only-password-2026!";

  @Autowired
  private MockMvc mvc;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private AdminMfaService service;

  @MockitoBean
  private AdminMfaRateLimiter rateLimiter;

  private UUID accountId;
  private String email;

  @BeforeEach
  void createAdmin() {
    email = "mfa-" + UUID.randomUUID() + "@example.com";

    var account = accounts.saveAndFlush(new UserAccount(
      email,
      passwordEncoder.encode(PASSWORD),
      UserRole.ADMIN
    ));

    accountId = account.getId();
  }

  @Test
  void shouldBlockAdminApiAfterPasswordLogin() throws Exception {
    Cookie session = login();

    mvc.perform(get("/api/admin/products").cookie(session))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));

    when(service.isEnabled(accountId)).thenReturn(true);

    mvc.perform(get("/api/auth/mfa/status").cookie(session))
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.enabled").value(true))
      .andExpect(jsonPath("$.verified").value(false));
  }

  @Test
  void shouldVerifyAndRotateSession() throws Exception {
    Cookie original = login();
    when(service.verifyTotp(accountId, "123456")).thenReturn(true);

    var result = submitCode(original, 204);
    Cookie verified = result.getResponse().getCookie("JSESSIONID");

    assertThat(verified).isNotNull();
    assertThat(verified.getValue()).isNotEqualTo(original.getValue());

    mvc.perform(get("/api/auth/session").cookie(verified))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(email));

    mvc.perform(get("/api/auth/mfa/status").cookie(verified))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.verified").value(true));

    mvc.perform(get("/api/auth/session").cookie(original))
      .andExpect(status().isNoContent());
  }

  @Test
  void shouldNotUnlockSessionForInvalidCode() throws Exception {
    Cookie session = login();
    when(service.verifyTotp(accountId, "123456")).thenReturn(false);

    submitCode(session, 400);

    mvc.perform(get("/api/admin/products").cookie(session))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
  }

  @Test
  void shouldRejectAttemptWhenRateLimitIsExceeded() throws Exception {
    Cookie session = login();
    when(rateLimiter.reserveAttempt(accountId)).thenReturn(60L);

    submitCode(session, 429);

    verify(service, never()).verifyTotp(any(), any());
  }

  @Test
  void shouldRejectVerificationWithoutCsrf() throws Exception {
    Cookie session = login();

    mvc.perform(post("/api/auth/mfa/verify")
        .cookie(session)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"code":"123456"}
          """))
      .andExpect(status().isForbidden());

    verifyNoInteractions(service, rateLimiter);
  }

  private MvcResult submitCode(Cookie session, int expectedStatus)
    throws Exception {

    Cookie csrf = csrfCookie(session);

    return mvc.perform(post("/api/auth/mfa/verify")
        .cookie(session, csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"code":"123456"}
          """))
      .andExpect(status().is(expectedStatus))
      .andReturn();
  }

  private Cookie login() throws Exception {
    Cookie csrf = csrfCookie(null);

    var result = mvc.perform(post("/api/auth/login")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .param("email", email)
        .param("password", PASSWORD))
      .andExpect(status().isAccepted())
      .andReturn();

    Cookie session = result.getResponse().getCookie("JSESSIONID");
    assertThat(session).isNotNull();
    return session;
  }

  private Cookie csrfCookie(Cookie session) throws Exception {
    var request = get("/api/auth/csrf");

    if (session != null) {
      request.cookie(session);
    }

    var result = mvc.perform(request)
      .andExpect(status().isNoContent())
      .andReturn();

    Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrf).isNotNull();
    return csrf;
  }
}
