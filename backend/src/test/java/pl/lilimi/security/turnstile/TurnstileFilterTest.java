package pl.lilimi.security.turnstile;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TurnstileFilterTest {

  @Mock
  private TurnstileVerifier verifier;

  @Mock
  private FilterChain chain;

  private TurnstileFilter filter;

  @BeforeEach
  void setUp() {
    filter = new TurnstileFilter(verifier);
  }

  @Test
  void shouldVerifyInquiryAndContinue() throws Exception {
    var request = request("/api/project-inquiries");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(verifier).verify("test-only-token", "project-inquiry");
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldVerifyRegistrationAndContinue() throws Exception {
    var request = request("/api/auth/register");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(verifier).verify("test-only-token", "registration");
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldRejectInvalidTokenWithoutContinuing() throws Exception {
    var request = request("/api/project-inquiries");
    var response = new MockHttpServletResponse();

    doThrow(new TurnstileRejectedException())
      .when(verifier)
      .verify("test-only-token", "project-inquiry");

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(response.getContentAsString())
      .isEqualTo("{\"code\":\"TURNSTILE_REJECTED\"}");

    verifyNoInteractions(chain);
  }

  @Test
  void shouldReturnServiceUnavailableWithoutContinuing() throws Exception {
    var request = request("/api/project-inquiries");
    var response = new MockHttpServletResponse();

    doThrow(new TurnstileUnavailableException())
      .when(verifier)
      .verify("test-only-token", "project-inquiry");

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getHeader("Retry-After")).isEqualTo("30");
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(response.getContentAsString())
      .isEqualTo("{\"code\":\"TURNSTILE_UNAVAILABLE\"}");

    verifyNoInteractions(chain);
  }

  @Test
  void shouldPassMissingTokenToVerifier() throws Exception {
    var request = new MockHttpServletRequest(
      "POST",
      "/api/auth/register"
    );
    var response = new MockHttpServletResponse();

    doThrow(new TurnstileRejectedException())
      .when(verifier)
      .verify(null, "registration");

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    verifyNoInteractions(chain);
  }

  @Test
  void shouldSkipUnprotectedEndpoint() throws Exception {
    var request = new MockHttpServletRequest(
      "POST",
      "/api/payments/stripe/webhook"
    );
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verifyNoInteractions(verifier);
    verify(chain).doFilter(request, response);
  }

  private MockHttpServletRequest request(String path) {
    var request = new MockHttpServletRequest("POST", path);
    request.addHeader("X-Turnstile-Token", "test-only-token");
    return request;
  }
}
