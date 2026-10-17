package pl.lilimi.payment.stripe;

import com.stripe.StripeClient;
import com.stripe.service.checkout.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
  prefix = "lilimi.payments.stripe",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = false
)
public class StripeConfiguration {

  @Bean
  public StripeClient stripeClient(
    @Value("${lilimi.payments.stripe.api-key:}")
    String apiKey
  ) {
    if (
      apiKey.isBlank()
        || !apiKey.equals(apiKey.strip())
        || !(
        apiKey.startsWith("sk_test_")
          || apiKey.startsWith("rk_test_")
      )
    ) {
      throw new IllegalStateException(
        "Stripe requires a sandbox API key"
      );
    }

    return StripeClient.builder()
      .setApiKey(apiKey)
      .setConnectTimeout(5000)
      .setReadTimeout(30000)
      .setMaxNetworkRetries(1)
      .build();
  }

  @Bean
  public SessionService stripeCheckoutSessions(
    StripeClient client
  ) {
    return client.v1().checkout().sessions();
  }
}
