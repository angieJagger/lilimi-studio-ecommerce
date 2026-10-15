package pl.lilimi.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.application.PasswordResetMailService;
import pl.lilimi.auth.application.PasswordResetService;
import pl.lilimi.auth.domain.PasswordResetToken;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.PasswordResetTokenRepository;
import pl.lilimi.auth.persistence.UserAccountRepository;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "spring.application.name=lilimi-password-reset-test"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class PasswordResetTest {

  private static final String EMAIL = "password-reset@example.com";
  private static final String OLD_PASSWORD = "Original-password-2026!";
  private static final String NEW_PASSWORD = "Changed-password-2026!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private PasswordResetTokenRepository tokens;

  @Autowired
  private PasswordResetService resetService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @MockitoBean
  private PasswordResetMailService mailService;

  private final JsonMapper mapper = JsonMapper.builder().build();
  private UUID accountId;

  @BeforeEach
  void createCustomer() {
    var account = new UserAccount(
      EMAIL,
      passwordEncoder.encode(OLD_PASSWORD),
      UserRole.CUSTOMER
    );
    account.requireEmailVerification();
    account.verifyEmail();
    accountId = accounts.saveAndFlush(account).getId();
  }

  @Test
  void shouldResetPasswordAndInvalidateExistingSession() throws Exception {
    var oldSession = login(OLD_PASSWORD, 204);
    assertThat(oldSession).isNotNull();

    mockMvc.perform(get("/api/auth/me").session(oldSession))
      .andExpect(status().isOk());

    var token = requestToken();
    reset(token, NEW_PASSWORD, 204);

    var account = accounts.findById(accountId).orElseThrow();
    assertThat(account.getCredentialsVersion()).isEqualTo(1);
    assertThat(account.isEmailVerified()).isTrue();
    assertThat(passwordEncoder.matches(NEW_PASSWORD, account.getPasswordHash()))
      .isTrue();
    assertThat(passwordEncoder.matches(OLD_PASSWORD, account.getPasswordHash()))
      .isFalse();

    mockMvc.perform(get("/api/auth/me").session(oldSession))
      .andExpect(status().isUnauthorized());
    assertThat(oldSession.isInvalid()).isTrue();

    // Reset does not automatically sign the customer in.
    mockMvc.perform(get("/api/auth/me"))
      .andExpect(status().isUnauthorized());

    login(OLD_PASSWORD, 401);
    var newSession = login(NEW_PASSWORD, 204);
    assertThat(newSession).isNotNull();
    mockMvc.perform(get("/api/auth/me").session(newSession))
      .andExpect(status().isOk());
  }

  @Test
  void shouldRejectAlreadyUsedToken() throws Exception {
    var token = requestToken();
    reset(token, NEW_PASSWORD, 204);
    reset(token, "Another-password-2026!", 400);

    assertPasswordUnchanged(NEW_PASSWORD, 1);
  }

  @Test
  void shouldInvalidateOtherTokensAfterSuccessfulReset() throws Exception {
    var firstToken = resetService.issueToken(accountId);
    var secondToken = resetService.issueToken(accountId);

    reset(firstToken, NEW_PASSWORD, 204);
    reset(secondToken, "Another-password-2026!", 400);

    assertPasswordUnchanged(NEW_PASSWORD, 1);
  }

  @Test
  void shouldRejectExpiredToken() throws Exception {
    String rawToken = "c".repeat(43);
    String hash = HexFormat.of().formatHex(
      MessageDigest.getInstance("SHA-256")
        .digest(rawToken.getBytes(StandardCharsets.UTF_8))
    );
    var now = Instant.now();

    tokens.saveAndFlush(new PasswordResetToken(
      accountId,
      hash,
      0,
      now.minusSeconds(3600),
      now.minusSeconds(1800)
    ));

    reset(rawToken, NEW_PASSWORD, 400);
    assertPasswordUnchanged(OLD_PASSWORD, 0);
  }

  @Test
  void shouldRejectResetWithoutCsrfWithoutConsumingToken() throws Exception {
    var token = requestToken();

    mockMvc.perform(post("/api/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(resetJson(token, NEW_PASSWORD)))
      .andExpect(status().isForbidden());

    assertPasswordUnchanged(OLD_PASSWORD, 0);
    reset(token, NEW_PASSWORD, 204);
  }

  @Test
  void shouldRejectInvalidPasswordsWithoutConsumingToken() throws Exception {
    var token = requestToken();

    for (var password : new String[]{"short", " ".repeat(12), "ą".repeat(37)}) {
      reset(token, password, 400);
      assertPasswordUnchanged(OLD_PASSWORD, 0);
    }

    reset(token, NEW_PASSWORD, 204);
  }

  @Test
  void shouldRejectUnknownToken() throws Exception {
    reset("z".repeat(43), NEW_PASSWORD, 400);
    assertPasswordUnchanged(OLD_PASSWORD, 0);
  }

  @Test
  void shouldAcceptUnknownEmailWithoutSendingMessage() throws Exception {
    var csrf = csrfCookie();

    mockMvc.perform(post("/api/auth/request-password-reset")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of(
          "email", "missing-reset@example.com",
          "language", "pl"
        ))))
      .andExpect(status().isNoContent());

    verifyNoInteractions(mailService);
    assertThat(tokens.count()).isZero();
  }

  private String requestToken() throws Exception {
    var csrf = csrfCookie();

    mockMvc.perform(post("/api/auth/request-password-reset")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of(
          "email", EMAIL,
          "language", "pl"
        ))))
      .andExpect(status().isNoContent());

    var captor = ArgumentCaptor.forClass(String.class);
    verify(mailService).send(eq(EMAIL), captor.capture(), eq("pl"));
    assertThat(captor.getValue()).matches("[A-Za-z0-9_-]{43}");
    return captor.getValue();
  }

  private void reset(String token, String password, int expectedStatus)
    throws Exception {

    var csrf = csrfCookie();

    mockMvc.perform(post("/api/auth/reset-password")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(resetJson(token, password)))
      .andExpect(status().is(expectedStatus));
  }

  private String resetJson(String token, String password) {
    return mapper.writeValueAsString(Map.of(
      "token", token,
      "password", password
    ));
  }

  private MockHttpSession login(String password, int expectedStatus)
    throws Exception {

    var csrf = csrfCookie();

    var result = mockMvc.perform(post("/api/auth/login")
        .cookie(csrf)
        .header("X-XSRF-TOKEN", csrf.getValue())
        .param("email", EMAIL)
        .param("password", password))
      .andExpect(status().is(expectedStatus))
      .andReturn();

    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private Cookie csrfCookie() throws Exception {
    var result = mockMvc.perform(get("/api/auth/csrf"))
      .andExpect(status().isNoContent())
      .andReturn();

    var cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private void assertPasswordUnchanged(String password, long version) {
    var account = accounts.findById(accountId).orElseThrow();
    assertThat(account.getCredentialsVersion()).isEqualTo(version);
    assertThat(passwordEncoder.matches(password, account.getPasswordHash()))
      .isTrue();
  }
}
