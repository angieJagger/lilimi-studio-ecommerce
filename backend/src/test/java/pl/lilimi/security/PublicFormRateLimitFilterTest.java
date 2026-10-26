package pl.lilimi.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.security.application.PublicFormRateLimiter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class PublicFormRateLimitFilterTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private PublicFormRateLimiter limiter;

  @Test
  void shouldRejectInquiryWhenLimitIsExhausted() throws Exception {
    when(limiter.reserveInquiry("127.0.0.1"))
      .thenReturn(120L);

    mvc.perform(post("/api/project-inquiries")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isTooManyRequests())
      .andExpect(header().string("Retry-After", "120"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.code").value("PUBLIC_FORM_RATE_LIMITED"));

    verify(limiter).reserveInquiry("127.0.0.1");
  }

  @Test
  void shouldRejectRegistrationWhenLimitIsExhausted() throws Exception {
    when(limiter.reserveRegistration("127.0.0.1"))
      .thenReturn(600L);

    mvc.perform(post("/api/auth/register")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isTooManyRequests())
      .andExpect(header().string("Retry-After", "600"))
      .andExpect(jsonPath("$.code").value("PUBLIC_FORM_RATE_LIMITED"));

    verify(limiter).reserveRegistration("127.0.0.1");
  }

  @Test
  void shouldRejectMissingCsrfBeforeCheckingLimit() throws Exception {
    mvc.perform(post("/api/project-inquiries")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden());

    mvc.perform(post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden());

    verifyNoInteractions(limiter);
  }

  @Test
  void shouldContinueToValidationWhenRequestIsAdmitted() throws Exception {
    when(limiter.reserveInquiry("127.0.0.1"))
      .thenReturn(0L);

    mvc.perform(post("/api/project-inquiries")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest());

    verify(limiter).reserveInquiry("127.0.0.1");
  }

  @Test
  void shouldReturnServiceUnavailableWhenStorageFails() throws Exception {
    when(limiter.reserveInquiry("127.0.0.1"))
      .thenThrow(
        new DataAccessResourceFailureException(
          "Test-only storage failure"
        )
      );

    mvc.perform(post("/api/project-inquiries")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isServiceUnavailable())
      .andExpect(header().string("Retry-After", "60"))
      .andExpect(jsonPath("$.code").value("PUBLIC_FORM_UNAVAILABLE"));
  }

  @Test
  void shouldNotLimitPublicCatalog() throws Exception {
    mvc.perform(get("/api/products"))
      .andExpect(status().isOk());

    verifyNoInteractions(limiter);
  }
}
