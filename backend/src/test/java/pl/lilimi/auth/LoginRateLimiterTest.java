package pl.lilimi.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.application.LoginRateLimiter;
import pl.lilimi.auth.persistence.LoginRateLimitRepository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
  "spring.application.name=login-rate-limiter-test",
  "lilimi.security.login.account-attempts=3",
  "lilimi.security.login.source-attempts=5",
  "lilimi.security.login.window-seconds=10"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
class LoginRateLimiterTest {
  private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
  @Autowired LoginRateLimiter limiter;
  @Autowired LoginRateLimitRepository repository;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean Clock clock;
  @Autowired MockMvc mvc;
  @Autowired UserAccountRepository accounts;
  @Autowired PasswordEncoder passwords;

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM login_rate_limits");
    when(clock.instant()).thenReturn(NOW);
  }

  @Test
  void shouldShareAccountLimitAcrossSourcesAndNormalizeEmail() {
    assertThat(limiter.reserveAttempt(" Person@Example.com ", "one")).isZero();
    assertThat(limiter.reserveAttempt("person@example.com", "two")).isZero();
    assertThat(limiter.reserveAttempt("PERSON@example.com", "three")).isZero();
    assertThat(limiter.reserveAttempt("person@example.com", "four")).isEqualTo(10);
    assertThat(limiter.reserveAttempt("other@example.com", "four")).isZero();
  }

  @Test
  void shouldLimitOneSourceAcrossDifferentAccounts() {
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.reserveAttempt("person" + i + "@example.com", "source")).isZero();
    }
    assertThat(limiter.reserveAttempt("another@example.com", "source")).isEqualTo(10);
    assertThat(limiter.reserveAttempt("another@example.com", "different-source")).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM login_rate_limits", Integer.class))
      .isEqualTo(8);
  }

  @Test
  void shouldExpireAtBoundaryWithoutExtendingWindowOnRejection() {
    for (int i = 0; i < 3; i++) limiter.reserveAttempt("person@example.com", "source");
    when(clock.instant()).thenReturn(NOW.plusMillis(1500));
    assertThat(limiter.reserveAttempt("person@example.com", "other")).isEqualTo(9);
    when(clock.instant()).thenReturn(NOW.plusSeconds(10));
    assertThat(limiter.reserveAttempt("person@example.com", "source")).isZero();
  }

  @Test
  void shouldKeepCountersWhenServiceIsRecreated() {
    for (int i = 0; i < 3; i++) limiter.reserveAttempt("person@example.com", "source");
    var recreated = new LoginRateLimiter(repository, clock, 3, 5, 10);
    assertThat(recreated.reserveAttempt("person@example.com", "new-source")).isEqualTo(10);
    assertThat(jdbc.queryForList("SELECT bucket_key FROM login_rate_limits", String.class))
      .allMatch(key -> key.matches("[0-9a-f]{64}"));
  }

  @Test
  void shouldAdmitOnlyAccountBudgetUnderConcurrentRequests() throws Exception {
    assertThat(concurrently(i -> limiter.reserveAttempt("person@example.com", "source-" + i)))
      .isEqualTo(3);
  }

  @Test
  void shouldAdmitOnlySourceBudgetUnderConcurrentRequests() throws Exception {
    assertThat(concurrently(i -> limiter.reserveAttempt("person-" + i + "@example.com", "source")))
      .isEqualTo(5);
  }

  @Test
  void shouldCleanOnlyExpiredBucketsPastGracePeriod() {
    jdbc.update("INSERT INTO login_rate_limits VALUES (?, 1, ?)",
      "a".repeat(64), Timestamp.from(NOW.minusSeconds(90000)));
    jdbc.update("INSERT INTO login_rate_limits VALUES (?, 1, ?)",
      "b".repeat(64), Timestamp.from(NOW.minusSeconds(1)));
    limiter.reserveAttempt("person@example.com", "source");
    limiter.removeExpiredBuckets();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM login_rate_limits", Integer.class))
      .isEqualTo(3);
  }

  @Test
  void shouldBlockEvenValidAdminCredentialsUntilWindowExpires() throws Exception {
    var account = accounts.saveAndFlush(new UserAccount(
      "rate-limit-admin@example.com", passwords.encode("Test-only-password-2026!"), UserRole.ADMIN));
    try {
      for (int i = 0; i < 3; i++) {
        mvc.perform(post("/api/auth/login").with(csrf())
            .param("email", account.getEmail()).param("password", "incorrect"))
          .andExpect(status().isUnauthorized());
      }
      mvc.perform(post("/api/auth/login").with(csrf())
          .param("email", account.getEmail()).param("password", "Test-only-password-2026!"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "10"));
      when(clock.instant()).thenReturn(NOW.plusSeconds(10));
      mvc.perform(post("/api/auth/login").with(csrf())
          .param("email", account.getEmail()).param("password", "Test-only-password-2026!"))
        .andExpect(status().isNoContent());
    } finally {
      accounts.deleteById(account.getId());
    }
  }

  private long concurrently(IntFunction<Long> attempt) throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(10)) {
      var results = new ArrayList<Future<Long>>();
      for (int i = 0; i < 20; i++) {
        int index = i;
        results.add(pool.submit(() -> {
          if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
          return attempt.apply(index);
        }));
      }
      start.countDown();
      long admitted = 0;
      for (var result : results) {
        if (result.get(20, TimeUnit.SECONDS) == 0) admitted++;
      }
      return admitted;
    }
  }
}
