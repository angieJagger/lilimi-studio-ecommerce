package pl.lilimi.security.turnstile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.security.application.PublicFormRateLimiter;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class TurnstileSecurityIntegrationTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private TurnstileVerifier verifier;

  @MockitoBean
  private PublicFormRateLimiter limiter;

  @Test
  void shouldExposeOnlyPublicConfigurationWithoutLogin() throws Exception {
    mvc.perform(get("/api/security/turnstile"))
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.enabled").value(false))
      .andExpect(jsonPath("$.siteKey").doesNotExist())
      .andExpect(jsonPath("$.secretKey").doesNotExist());

    verifyNoInteractions(verifier, limiter);
  }

  @Test
  void shouldCheckRateLimitBeforeTurnstile() throws Exception {
    when(limiter.reserveInquiry("127.0.0.1"))
      .thenReturn(120L);

    mvc.perform(post("/api/project-inquiries")
        .with(csrf())
        .header("X-Turnstile-Token", "test-only-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isTooManyRequests())
      .andExpect(jsonPath("$.code")
        .value("PUBLIC_FORM_RATE_LIMITED"));

    verifyNoInteractions(verifier);
  }

  @Test
  void shouldCheckCsrfBeforeTurnstile() throws Exception {
    mvc.perform(post("/api/project-inquiries")
        .header("X-Turnstile-Token", "test-only-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden());

    verify(limiter).reserveInquiry("127.0.0.1");
    verifyNoInteractions(verifier);
  }

  @Test
  void shouldRejectInquiryAfterValidCsrf() throws Exception {
    doThrow(new TurnstileRejectedException())
      .when(verifier)
      .verify("test-only-token", "project-inquiry");

    mvc.perform(post("/api/project-inquiries")
        .with(csrf())
        .header("X-Turnstile-Token", "test-only-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("TURNSTILE_REJECTED"));

    verify(verifier).verify("test-only-token", "project-inquiry");
  }

  @Test
  void shouldRejectRegistrationAfterValidCsrf() throws Exception {
    doThrow(new TurnstileRejectedException())
      .when(verifier)
      .verify("test-only-token", "registration");

    mvc.perform(post("/api/auth/register")
        .with(csrf())
        .header("X-Turnstile-Token", "test-only-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("TURNSTILE_REJECTED"));

    verify(verifier).verify("test-only-token", "registration");
  }
}
