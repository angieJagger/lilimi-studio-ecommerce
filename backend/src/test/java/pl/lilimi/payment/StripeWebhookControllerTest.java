package pl.lilimi.payment;

import com.stripe.model.Event;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.payment.application.StripeWebhookProcessor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=true",
  "lilimi.payments.stripe.api-key=sk_test_placeholder_for_mocked_tests",
  "lilimi.payments.stripe.webhook-secret=whsec_controller_test",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class StripeWebhookControllerTest {

  private static final String URL =
    "/api/payments/stripe/webhook";

  private static final String SECRET =
    "whsec_controller_test";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private StripeWebhookProcessor processor;

  @Test
  void shouldAcceptSignedWebhookWithoutLoginOrCsrf()
    throws Exception {

    String payload = payload();

    mockMvc.perform(post(URL)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload.getBytes(StandardCharsets.UTF_8))
        .header("Stripe-Signature", sign(payload)))
      .andExpect(status().isNoContent());

    var captured = ArgumentCaptor.forClass(Event.class);

    verify(processor).process(captured.capture());

    assertThat(captured.getValue().getId())
      .isEqualTo("evt_test_http");
    assertThat(captured.getValue().getType())
      .isEqualTo("checkout.session.completed");
    assertThat(captured.getValue().getLivemode()).isFalse();
  }

  @Test
  void shouldRejectMissingSignature() throws Exception {
    mockMvc.perform(post(URL)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload().getBytes(StandardCharsets.UTF_8)))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(processor);
  }

  @Test
  void shouldRejectBodyChangedAfterSigning()
    throws Exception {

    String original = payload();
    String signature = sign(original);

    String changed = original.replace(
      "Zażółć gęślą jaźń",
      "Changed description"
    );

    mockMvc.perform(post(URL)
        .contentType(MediaType.APPLICATION_JSON)
        .content(changed.getBytes(StandardCharsets.UTF_8))
        .header("Stripe-Signature", signature))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(processor);
  }

  @Test
  void shouldRejectSignatureCreatedWithDifferentSecret()
    throws Exception {

    String payload = payload();

    mockMvc.perform(post(URL)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload.getBytes(StandardCharsets.UTF_8))
        .header(
          "Stripe-Signature",
          sign(
            payload,
            Instant.now().getEpochSecond(),
            "whsec_different_secret"
          )
        ))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(processor);
  }

  @Test
  void shouldReturnFailureWhenProcessingFails()
    throws Exception {

    doThrow(new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "STRIPE_WEBHOOK_API_VERSION_MISMATCH"
    )).when(processor).process(any(Event.class));

    String payload = payload();

    mockMvc.perform(post(URL)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload.getBytes(StandardCharsets.UTF_8))
        .header("Stripe-Signature", sign(payload)))
      .andExpect(status().isServiceUnavailable());

    verify(processor).process(any(Event.class));
  }

  @Test
  void shouldStillRequireCsrfForCustomerPaymentEndpoint()
    throws Exception {

    mockMvc.perform(post(
        "/api/orders/"
          + UUID.randomUUID()
          + "/payment-session"
      )
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isForbidden());

    verifyNoInteractions(processor);
  }

  private static String payload() {
    return """
      {
        "id": "evt_test_http",
        "object": "event",
        "type": "checkout.session.completed",
        "livemode": false,
        "data": {
          "object": {
            "id": "cs_test_http",
            "object": "checkout.session",
            "metadata": {
              "description": "Zażółć gęślą jaźń"
            }
          }
        }
      }
      """;
  }

  private static String sign(String payload)
    throws Exception {

    return sign(
      payload,
      Instant.now().getEpochSecond(),
      SECRET
    );
  }

  private static String sign(
    String payload,
    long timestamp,
    String secret
  ) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");

    mac.init(new SecretKeySpec(
      secret.getBytes(StandardCharsets.UTF_8),
      "HmacSHA256"
    ));

    byte[] digest = mac.doFinal(
      (timestamp + "." + payload)
        .getBytes(StandardCharsets.UTF_8)
    );

    return "t=" + timestamp
      + ",v1="
      + HexFormat.of().formatHex(digest);
  }
}
