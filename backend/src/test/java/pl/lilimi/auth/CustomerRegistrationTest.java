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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.UUID;

import pl.lilimi.auth.domain.EmailVerificationToken;
import pl.lilimi.auth.persistence.EmailVerificationTokenRepository;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.auth.application.EmailVerificationMailService;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(properties = {
  "spring.application.name=lilimi-customer-registration-test"
})
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
  @Autowired
  private EmailVerificationTokenRepository verificationTokens;
  @MockitoBean
  private EmailVerificationMailService mailService;

  @Test
  void shouldRegisterCustomerAndAllowLoginOnlyAfterEmailVerification()
    throws Exception {

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
    assertThat(account.isEmailVerificationRequired()).isTrue();
    assertThat(account.isEmailVerified()).isFalse();
    assertThat(account.getPasswordHash()).isNotEqualTo(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, account.getPasswordHash()))
      .isTrue();

    var tokenCaptor = ArgumentCaptor.forClass(String.class);

    verify(mailService).send(
      eq(EMAIL),
      tokenCaptor.capture(),
      eq("en")
    );

    String token = tokenCaptor.getValue();
    assertThat(token).matches("[A-Za-z0-9_-]{43}");

    var beforeVerificationCsrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/login")
        .cookie(beforeVerificationCsrf)
        .header("X-XSRF-TOKEN", beforeVerificationCsrf.getValue())
        .param("email", EMAIL)
        .param("password", PASSWORD))
      .andExpect(status().isUnauthorized());

    var verificationCsrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/verify-email")
        .cookie(verificationCsrf)
        .header("X-XSRF-TOKEN", verificationCsrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
        {"token": "%s"}
        """.formatted(token)))
      .andExpect(status().isNoContent());

    assertThat(accounts.findByEmail(EMAIL).orElseThrow().isEmailVerified())
      .isTrue();

    // Verification does not sign the customer in automatically.
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

  @Test
  void shouldRejectAlreadyUsedVerificationToken() throws Exception {
    var token = registerAndCaptureToken();

    verifyToken(token, 204);
    verifyToken(token, 400);
  }

  @Test
  void shouldRejectVerificationWithoutCsrf() throws Exception {
    var token = registerAndCaptureToken();

    mockMvc.perform(post("/api/auth/verify-email")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
        {"token": "%s"}
        """.formatted(token)))
      .andExpect(status().isForbidden());

    assertThat(accounts.findByEmail(EMAIL).orElseThrow().isEmailVerified())
      .isFalse();
  }

  @Test
  void shouldRejectExpiredVerificationToken() throws Exception {
    var account = accounts.saveAndFlush(new UserAccount(
      "expired-verification@example.com",
      passwordEncoder.encode(PASSWORD),
      UserRole.CUSTOMER
    ));

    account.requireEmailVerification();
    accounts.flush();

    String rawToken = "a".repeat(43);

    String hash = java.util.HexFormat.of().formatHex(
      java.security.MessageDigest.getInstance("SHA-256")
        .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    );

    var now = Instant.now();

    verificationTokens.saveAndFlush(new EmailVerificationToken(
      account.getId(),
      hash,
      now.minusSeconds(7200),
      now.minusSeconds(3600)
    ));

    verifyToken(rawToken, 400);

    assertThat(accounts.findById(account.getId()).orElseThrow().isEmailVerified())
      .isFalse();
  }

  @Test
  void shouldAcceptResendForUnknownEmailWithoutSendingMessage()
    throws Exception {

    var csrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/resend-verification")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
        {
          "email": "unknown@example.com",
          "language": "pl"
        }
        """))
      .andExpect(status().isNoContent());

    verifyNoInteractions(mailService);
  }

  @Test
  void shouldNotResendImmediatelyAfterRegistration() throws Exception {
    registerAndCaptureToken();

    long tokensBefore = verificationTokens.count();
    var csrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/resend-verification")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
        {
          "email": "registration-test@example.com",
          "language": "en"
        }
        """))
      .andExpect(status().isNoContent());

    assertThat(verificationTokens.count()).isEqualTo(tokensBefore);

    verify(mailService, times(1)).send(
      eq(EMAIL),
      org.mockito.ArgumentMatchers.anyString(),
      eq("en")
    );
  }

  @Test
  void shouldNotResendForVerifiedAccount() throws Exception {
    var token = registerAndCaptureToken();
    verifyToken(token, 204);

    long tokensBefore = verificationTokens.count();
    var csrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/resend-verification")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
        {
          "email": "registration-test@example.com",
          "language": "en"
        }
        """))
      .andExpect(status().isNoContent());

    assertThat(verificationTokens.count()).isEqualTo(tokensBefore);

    verify(mailService, times(1)).send(
      eq(EMAIL),
      org.mockito.ArgumentMatchers.anyString(),
      eq("en")
    );
  }

  @Test
  void shouldRejectResendWithoutCsrf() throws Exception {
    mockMvc.perform(post("/api/auth/resend-verification")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
        {
          "email": "registration-test@example.com",
          "language": "pl"
        }
        """))
      .andExpect(status().isForbidden());

    verifyNoInteractions(mailService);
  }

  private String registerAndCaptureToken() throws Exception {
    var csrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/register")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson(EMAIL, PASSWORD)))
      .andExpect(status().isCreated());

    var captor = ArgumentCaptor.forClass(String.class);
    verify(mailService).send(eq(EMAIL), captor.capture(), eq("en"));

    return captor.getValue();
  }

  private void verifyToken(String token, int expectedStatus) throws Exception {
    var csrf = getCsrfCookie();

    mockMvc.perform(post("/api/auth/verify-email")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
        {"token": "%s"}
        """.formatted(token)))
      .andExpect(status().is(expectedStatus));
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
