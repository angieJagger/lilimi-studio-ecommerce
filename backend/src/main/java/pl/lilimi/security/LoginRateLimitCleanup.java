package pl.lilimi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DataAccessException;
import pl.lilimi.auth.application.LoginRateLimiter;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class LoginRateLimitCleanup {
  private static final Logger log = LoggerFactory.getLogger(LoginRateLimitCleanup.class);
  private final LoginRateLimiter limiter;

  public LoginRateLimitCleanup(LoginRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Scheduled(initialDelayString = "${lilimi.security.login.cleanup-delay-ms:3600000}",
    fixedDelayString = "${lilimi.security.login.cleanup-delay-ms:3600000}")
  public void cleanUp() {
    try {
      limiter.removeExpiredBuckets();
    } catch (DataAccessException exception) {
      log.error("Expired login rate limit buckets could not be removed");
    }
  }
}
