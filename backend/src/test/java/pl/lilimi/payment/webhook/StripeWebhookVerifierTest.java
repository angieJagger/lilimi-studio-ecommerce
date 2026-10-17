package pl.lilimi.payment.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeWebhookVerifierTest {

  private static final String SECRET =
    "whsec_test_secret_for_unit_tests";

  private final StripeWebhookVerifier verifier =
    new StripeWebhookVerifier(SECRET);

  @Test
  void shouldAcceptCorrectlySignedTestEvent() throws Exception {
    String payload = payload(false);
    long timestamp = Instant.now().getEpochSecond();

    var event = verifier.verify(
      payload,
      sign(payload, timestamp)
    );

    assertThat(event.getId()).isEqualTo("evt_test_verified");
    assertThat(event.getType())
      .isEqualTo("checkout.session.completed");
    assertThat(event.getLivemode()).isFalse();
  }

  @Test
  void shouldRejectPayloadChangedAfterSigning() throws Exception {
    String original = payload(false);
    String signature = sign(
      original,
      Instant.now().getEpochSecond()
    );

    String changed = original.replace(
      "evt_test_verified",
      "evt_test_changed"
    );

    assertRejected(changed, signature, 400);
  }

  @Test
  void shouldRejectMissingSignature() {
    assertRejected(payload(false), null, 400);
  }

  @Test
  void shouldRejectOldSignature() throws Exception {
    String payload = payload(false);
    long timestamp = Instant.now()
      .minusSeconds(3600)
      .getEpochSecond();

    assertRejected(
      payload,
      sign(payload, timestamp),
      400
    );
  }

  @Test
  void shouldRejectLiveEventEvenWithValidSignature()
    throws Exception {

    String payload = payload(true);

    assertRejected(
      payload,
      sign(payload, Instant.now().getEpochSecond()),
      400
    );
  }

  @Test
  void shouldRejectMissingConfiguration() {
    var unconfigured = new StripeWebhookVerifier("");

    assertThatThrownBy(() ->
      unconfigured.verify(payload(false), "anything")
    ).isInstanceOfSatisfying(
      ResponseStatusException.class,
      exception -> {
        assertThat(exception.getStatusCode().value())
          .isEqualTo(503);
        assertThat(exception.getReason())
          .isEqualTo("STRIPE_WEBHOOK_NOT_CONFIGURED");
      }
    );
  }

  private void assertRejected(
    String payload,
    String signature,
    int expectedStatus
  ) {
    assertThatThrownBy(() ->
      verifier.verify(payload, signature)
    ).isInstanceOfSatisfying(
      ResponseStatusException.class,
      exception -> {
        assertThat(exception.getStatusCode().value())
          .isEqualTo(expectedStatus);
        assertThat(exception.getReason())
          .isEqualTo("STRIPE_WEBHOOK_INVALID");
      }
    );
  }

  private static String payload(boolean livemode) {
    return """
      {
        "id": "evt_test_verified",
        "object": "event",
        "type": "checkout.session.completed",
        "livemode": %s,
        "data": {
          "object": {
            "id": "cs_test_verified",
            "object": "checkout.session"
          }
        }
      }
      """.formatted(livemode);
  }

  private static String sign(
    String payload,
    long timestamp
  ) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");

    mac.init(
      new SecretKeySpec(
        SECRET.getBytes(StandardCharsets.UTF_8),
        "HmacSHA256"
      )
    );

    byte[] digest = mac.doFinal(
      (timestamp + "." + payload)
        .getBytes(StandardCharsets.UTF_8)
    );

    return "t=" + timestamp
      + ",v1="
      + HexFormat.of().formatHex(digest);
  }
}
