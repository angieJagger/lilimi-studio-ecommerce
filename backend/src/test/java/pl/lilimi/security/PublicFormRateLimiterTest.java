package pl.lilimi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.security.application.PublicFormRateLimiter;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
  "spring.application.name=public-form-rate-limiter-test"
})
@Import(PostgresTestConfiguration.class)
class PublicFormRateLimiterTest {

  private static final Instant NOW =
    Instant.parse("2026-10-26T12:00:00Z");

  private static final String SOURCE = "203.0.113.10";

  @Autowired
  private PublicFormRateLimiter limiter;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private Clock clock;

  @BeforeEach
  void resetCountersAndClock() {
    jdbc.update("DELETE FROM request_rate_limits");
    when(clock.instant()).thenReturn(NOW);
  }

  @Test
  void shouldAllowFiveInquiryAttemptsAndRejectSixth() {
    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(limiter.reserveInquiry(SOURCE)).isZero();
    }

    assertThat(limiter.reserveInquiry(SOURCE))
      .isEqualTo(900);
  }

  @Test
  void shouldAllowTenRegistrationAttemptsAndRejectEleventh() {
    for (int attempt = 0; attempt < 10; attempt++) {
      assertThat(limiter.reserveRegistration(SOURCE)).isZero();
    }

    assertThat(limiter.reserveRegistration(SOURCE))
      .isEqualTo(3600);
  }

  @Test
  void shouldKeepInquiryAndRegistrationBudgetsSeparate() {
    for (int attempt = 0; attempt < 5; attempt++) {
      limiter.reserveInquiry(SOURCE);
    }

    assertThat(limiter.reserveInquiry(SOURCE)).isPositive();

    for (int attempt = 0; attempt < 10; attempt++) {
      assertThat(limiter.reserveRegistration(SOURCE)).isZero();
    }

    assertThat(limiter.reserveRegistration(SOURCE))
      .isEqualTo(3600);
  }

  @Test
  void shouldKeepSourceAddressesSeparate() {
    for (int attempt = 0; attempt < 5; attempt++) {
      limiter.reserveInquiry(SOURCE);
    }

    assertThat(limiter.reserveInquiry(SOURCE)).isPositive();

    assertThat(limiter.reserveInquiry("203.0.113.20"))
      .isZero();
  }

  @Test
  void shouldRestoreEachBudgetAtItsOwnExpiry() {
    for (int attempt = 0; attempt < 5; attempt++) {
      limiter.reserveInquiry(SOURCE);
    }

    for (int attempt = 0; attempt < 10; attempt++) {
      limiter.reserveRegistration(SOURCE);
    }

    when(clock.instant()).thenReturn(
      NOW.plusSeconds(900)
    );

    assertThat(limiter.reserveInquiry(SOURCE)).isZero();

    assertThat(limiter.reserveRegistration(SOURCE))
      .isEqualTo(2700);

    when(clock.instant()).thenReturn(
      NOW.plusSeconds(3600)
    );

    assertThat(limiter.reserveRegistration(SOURCE))
      .isZero();
  }
}
