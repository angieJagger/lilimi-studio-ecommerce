package pl.lilimi.auth.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class EmailVerificationConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
