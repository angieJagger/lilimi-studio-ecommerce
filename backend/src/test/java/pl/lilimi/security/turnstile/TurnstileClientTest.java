package pl.lilimi.security.turnstile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnstileClientTest {

  private static final String VERIFY_URL =
    "https://challenges.cloudflare.com/turnstile/v0/siteverify";

  private MockRestServiceServer server;
  private TurnstileClient client;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder()
      .baseUrl("https://challenges.cloudflare.com");

    server = MockRestServiceServer.bindTo(builder).build();

    client = new TurnstileClient(
      builder.build(),
      new TurnstileProperties(
        true,
        "test-only-secret",
        "lilimistudio.com"
      )
    );
  }

  @Test
  void shouldSendFormAndReadSuccessfulResponse() {
    server.expect(requestTo(VERIFY_URL))
      .andExpect(method(HttpMethod.POST))
      .andExpect(content().contentTypeCompatibleWith(
        MediaType.APPLICATION_FORM_URLENCODED
      ))
      .andExpect(content().string(
        "secret=test-only-secret&response=test-only-token"
      ))
      .andRespond(withSuccess("""
        {
          "success": true,
          "hostname": "lilimistudio.com",
          "action": "project-inquiry",
          "challenge_ts": "2026-11-07T12:00:00Z"
        }
        """, MediaType.APPLICATION_JSON));

    var response = client.verify("test-only-token");

    assertThat(response.isSuccessful()).isTrue();
    assertThat(response.hostname()).isEqualTo("lilimistudio.com");
    assertThat(response.action()).isEqualTo("project-inquiry");
    server.verify();
  }

  @Test
  void shouldReadRejectedTokenResponse() {
    server.expect(requestTo(VERIFY_URL))
      .andRespond(withSuccess("""
        {
          "success": false,
          "error-codes": ["timeout-or-duplicate"]
        }
        """, MediaType.APPLICATION_JSON));

    var response = client.verify("test-only-token");

    assertThat(response.isSuccessful()).isFalse();
    assertThat(response.errorCodes())
      .containsExactly("timeout-or-duplicate");
    server.verify();
  }

  @Test
  void shouldRejectResponseWithoutVerificationResult() {
    server.expect(requestTo(VERIFY_URL))
      .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.verify("test-only-token"))
      .isInstanceOf(TurnstileUnavailableException.class);

    server.verify();
  }

  @Test
  void shouldTreatHttpFailureAsServiceUnavailable() {
    server.expect(requestTo(VERIFY_URL))
      .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> client.verify("test-only-token"))
      .isInstanceOf(TurnstileUnavailableException.class);

    server.verify();
  }

  @Test
  void shouldTreatMalformedResponseAsServiceUnavailable() {
    server.expect(requestTo(VERIFY_URL))
      .andRespond(withSuccess("{invalid", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.verify("test-only-token"))
      .isInstanceOf(TurnstileUnavailableException.class);

    server.verify();
  }
}
