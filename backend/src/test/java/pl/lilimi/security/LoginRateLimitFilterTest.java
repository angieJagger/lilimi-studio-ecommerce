package pl.lilimi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.application.LoginRateLimiter;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class LoginRateLimitFilterTest {
  @Autowired MockMvc mvc;
  @MockitoBean LoginRateLimiter limiter;

  @BeforeEach
  void resetLimiter() {
    reset(limiter);
  }

  @Test
  void shouldRejectExhaustedLoginBeforeCheckingPassword() throws Exception {
    when(limiter.reserveAttempt("missing@example.com", "127.0.0.1")).thenReturn(123L);
    mvc.perform(post("/api/auth/login").with(csrf())
        .param("email", "missing@example.com").param("password", "anything"))
      .andExpect(status().isTooManyRequests())
      .andExpect(header().string("Retry-After", "123"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    verify(limiter, times(1)).reserveAttempt("missing@example.com", "127.0.0.1");
  }

  @Test
  void shouldKeepCsrfProtectionBeforeRateLimit() throws Exception {
    mvc.perform(post("/api/auth/login").param("email", "person@example.com"))
      .andExpect(status().isForbidden());
    verifyNoInteractions(limiter);
  }

  @Test
  void shouldKeepGenericAuthenticationFailureWhenAdmitted() throws Exception {
    mvc.perform(post("/api/auth/login").with(csrf())
        .param("email", "missing@example.com").param("password", "anything"))
      .andExpect(status().isUnauthorized());
    verify(limiter).reserveAttempt("missing@example.com", "127.0.0.1");
  }

  @Test
  void shouldFailClosedWhenStorageIsUnavailable() throws Exception {
    when(limiter.reserveAttempt(any(), any()))
      .thenThrow(new DataAccessResourceFailureException("Test-only storage failure"));
    mvc.perform(post("/api/auth/login").with(csrf()).param("email", "person@example.com"))
      .andExpect(status().isServiceUnavailable())
      .andExpect(header().string("Retry-After", "60"))
      .andExpect(jsonPath("$.code").value("LOGIN_UNAVAILABLE"));
  }

  @Test
  void shouldNotLimitBrowsingOrLogout() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isOk());
    mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
    verifyNoInteractions(limiter);
  }

  @Test
  void shouldNotReadSpoofedForwardingHeaderInFilter() throws Exception {
    mvc.perform(post("/api/auth/login").with(csrf())
        .header("X-Forwarded-For", "203.0.113.45")
        .param("email", "missing@example.com").param("password", "anything"))
      .andExpect(status().isUnauthorized());
    verify(limiter).reserveAttempt("missing@example.com", "127.0.0.1");
  }
}
