package pl.lilimi.payment.cancellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression(
  "${lilimi.payments.stripe.enabled:false}"
    + " && ${lilimi.payments.stripe.expiration.enabled:true}"
)
public class CheckoutExpirationSchedulingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(
    CheckoutExpirationSchedulingConfiguration.class
  );

  private static final int BATCH_SIZE = 10;

  private final CheckoutExpirationProcessor processor;

  public CheckoutExpirationSchedulingConfiguration(
    CheckoutExpirationProcessor processor
  ) {
    this.processor = processor;
  }

  @Scheduled(
    initialDelayString =
      "${lilimi.payments.stripe.expiration.initial-delay-ms:10000}",
    fixedDelayString =
      "${lilimi.payments.stripe.expiration.poll-delay-ms:10000}"
  )
  public void processQueue() {
    try {
      for (int index = 0; index < BATCH_SIZE; index++) {
        if (!processor.processNext()) {
          return;
        }
      }
    } catch (RuntimeException exception) {
      log.error(
        "Checkout expiration queue processing failed. "
          + "Processing will be attempted again on the next scheduled run."
      );
    }
  }
}
