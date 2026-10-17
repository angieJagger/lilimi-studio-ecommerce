package pl.lilimi.payment.webhook;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StripeWebhookVerifier {

  private final String secret;

  public StripeWebhookVerifier(
    @Value("${lilimi.payments.stripe.webhook-secret:}")
    String secret
  ) {
    this.secret = secret;
  }

  public Event verify(String payload, String signature) {
    if (
      secret == null
        || !secret.startsWith("whsec_")
        || secret.length() <= "whsec_".length()
        || !secret.equals(secret.strip())
    ) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "STRIPE_WEBHOOK_NOT_CONFIGURED"
      );
    }

    if (
      payload == null
        || payload.isBlank()
        || signature == null
        || signature.isBlank()
    ) {
      throw invalidWebhook();
    }

    Event event;

    try {
      event = Webhook.constructEvent(
        payload,
        signature,
        secret
      );
    } catch (SignatureVerificationException exception) {
      throw invalidWebhook();
    } catch (RuntimeException exception) {
      throw invalidWebhook();
    }

    if (
      event == null
        || event.getId() == null
        || !event.getId().startsWith("evt_")
        || event.getId().length() <= 4
        || event.getId().length() > 255
        || event.getType() == null
        || event.getType().isBlank()
        || event.getType().length() > 100
        || !Boolean.FALSE.equals(event.getLivemode())
    ) {
      throw invalidWebhook();
    }

    return event;
  }

  private static ResponseStatusException invalidWebhook() {
    return new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "STRIPE_WEBHOOK_INVALID"
    );
  }
}
