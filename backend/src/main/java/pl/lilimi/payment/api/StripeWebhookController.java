package pl.lilimi.payment.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.payment.application.StripeWebhookProcessor;
import pl.lilimi.payment.infrastructure.StripeWebhookVerifier;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/payments/stripe/webhook")
public class StripeWebhookController {

  private final StripeWebhookVerifier verifier;
  private final StripeWebhookProcessor processor;
  private final boolean enabled;

  public StripeWebhookController(
    StripeWebhookVerifier verifier,
    StripeWebhookProcessor processor,
    @Value("${lilimi.payments.stripe.enabled:false}")
    boolean enabled
  ) {
    this.verifier = verifier;
    this.processor = processor;
    this.enabled = enabled;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void receive(
    @RequestBody byte[] body,
    @RequestHeader(
      name = "Stripe-Signature",
      required = false
    )
    String signature
  ) {
    if (!enabled) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENTS_DISABLED"
      );
    }

    String payload = new String(
      body,
      StandardCharsets.UTF_8
    );

    var event = verifier.verify(payload, signature);

    processor.process(event);
  }
}
