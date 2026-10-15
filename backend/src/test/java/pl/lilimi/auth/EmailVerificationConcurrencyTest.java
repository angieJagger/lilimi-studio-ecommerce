package pl.lilimi.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.application.EmailVerificationMailService;
import pl.lilimi.auth.application.EmailVerificationService;
import pl.lilimi.auth.application.ResendEmailVerificationService;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.EmailVerificationTokenRepository;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
  "spring.application.name=lilimi-email-verification-concurrency-test"
})
@Import(PostgresTestConfiguration.class)
class EmailVerificationConcurrencyTest {

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private EmailVerificationTokenRepository tokens;

  @Autowired
  private EmailVerificationService verificationService;

  @Autowired
  private ResendEmailVerificationService resendService;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private EmailVerificationMailService mailService;

  private UUID accountId;
  private String email;

  @BeforeEach
  void createPendingAccount() {
    email = "concurrent-" + UUID.randomUUID() + "@example.com";
    var account = new UserAccount(
      email,
      "{noop}unused-test-password",
      UserRole.CUSTOMER
    );
    account.requireEmailVerification();
    accountId = accounts.saveAndFlush(account).getId();
  }

  @AfterEach
  void removeTestAccount() {
    if (accountId != null) {
      // Token rows are removed by the foreign key's ON DELETE CASCADE.
      jdbc.update("DELETE FROM user_accounts WHERE id = ?", accountId);
    }
  }

  @Test
  void shouldSendOnlyOneMessageForConcurrentResendRequests() throws Exception {
    var results = concurrently(
      () -> {
        resendService.resend(email, "pl");
        return true;
      },
      () -> {
        resendService.resend(email, "pl");
        return true;
      }
    );

    assertThat(results).containsExactly(true, true);
    assertThat(tokens.countByAccountIdAndCreatedAtAfter(
      accountId,
      Instant.EPOCH
    )).isEqualTo(1);

    verify(mailService, times(1)).send(eq(email), anyString(), eq("pl"));
  }

  @Test
  void shouldAllowOnlyOneConcurrentVerificationWithDifferentTokens()
    throws Exception {

    var firstToken = verificationService.issueToken(accountId);
    var secondToken = verificationService.issueToken(accountId);

    var results = concurrently(
      () -> verifyOrReject(firstToken),
      () -> verifyOrReject(secondToken)
    );

    assertThat(results).containsExactlyInAnyOrder(true, false);
    assertThat(accounts.findById(accountId).orElseThrow().isEmailVerified())
      .isTrue();
  }

  private boolean verifyOrReject(String token) {
    try {
      verificationService.verify(token);
      return true;
    } catch (ResponseStatusException exception) {
      assertThat(exception.getStatusCode().value()).isEqualTo(400);
      return false;
    }
  }

  private <T> List<T> concurrently(Callable<T> first, Callable<T> second)
    throws Exception {

    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);

    try {
      var firstResult = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Concurrent test start timed out");
        }
        return first.call();
      });

      var secondResult = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Concurrent test start timed out");
        }
        return second.call();
      });

      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      return List.of(
        firstResult.get(20, TimeUnit.SECONDS),
        secondResult.get(20, TimeUnit.SECONDS)
      );
    } finally {
      start.countDown();
      executor.shutdownNow();
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent test workers did not stop");
      }
    }
  }
}
