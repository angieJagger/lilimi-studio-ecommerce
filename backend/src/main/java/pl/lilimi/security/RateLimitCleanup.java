package pl.lilimi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pl.lilimi.security.application.RequestRateLimiter;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class RateLimitCleanup {

  private static final Logger log =
    LoggerFactory.getLogger(RateLimitCleanup.class);

  private final RequestRateLimiter limiter;

  public RateLimitCleanup(RequestRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Scheduled(
    initialDelayString =
      "${lilimi.security.rate-limit.cleanup-delay-ms:3600000}",
    fixedDelayString =
      "${lilimi.security.rate-limit.cleanup-delay-ms:3600000}"
  )
  public void cleanUp() {
    try {
      limiter.removeExpiredBuckets();
    } catch (DataAccessException exception) {
      log.error("Expired rate limit buckets could not be removed");
    }
  }
}
