package pl.lilimi.auth.mfa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminMfaRepositoryTest {

  @Autowired
  private AdminMfaRepository mfa;

  @Autowired
  private UserAccountRepository accounts;

  private UUID accountId;

  @BeforeEach
  void createAdmin() {
    var account = new UserAccount(
      "mfa-" + UUID.randomUUID() + "@example.com",
      "unused-test-password-hash",
      UserRole.ADMIN
    );

    accountId = accounts.saveAndFlush(account).getId();
  }

  @Test
  void shouldCreatePendingConfigurationWithoutReplacingSecret() {
    assertThat(mfa.createPending(accountId, "encrypted-first"))
      .isTrue();

    assertThat(mfa.createPending(accountId, "encrypted-second"))
      .isFalse();

    var state = mfa.lock(accountId).orElseThrow();

    assertThat(state.encryptedSecret()).isEqualTo("encrypted-first");
    assertThat(state.enabled()).isFalse();
    assertThat(state.lastUsedTimeStep()).isNull();
  }

  @Test
  void shouldEnableOnlyOnceAndConsumeActivationCode() {
    mfa.createPending(accountId, "encrypted-secret");

    assertThat(mfa.enable(accountId, 100)).isTrue();
    assertThat(mfa.enable(accountId, 101)).isFalse();

    var state = mfa.lock(accountId).orElseThrow();

    assertThat(state.enabled()).isTrue();
    assertThat(state.lastUsedTimeStep()).isEqualTo(100L);
    assertThat(mfa.consumeTimeStep(accountId, 100)).isFalse();
  }

  @Test
  void shouldRejectRepeatedAndOlderTimeSteps() {
    mfa.createPending(accountId, "encrypted-secret");
    mfa.enable(accountId, 100);

    assertThat(mfa.consumeTimeStep(accountId, 101)).isTrue();
    assertThat(mfa.consumeTimeStep(accountId, 101)).isFalse();
    assertThat(mfa.consumeTimeStep(accountId, 100)).isFalse();
    assertThat(mfa.consumeTimeStep(accountId, 102)).isTrue();

    assertThat(mfa.lock(accountId).orElseThrow().lastUsedTimeStep())
      .isEqualTo(102L);
  }

  @Test
  void shouldNotConsumeCodeBeforeActivation() {
    mfa.createPending(accountId, "encrypted-secret");

    assertThat(mfa.consumeTimeStep(accountId, 100)).isFalse();
    assertThat(mfa.lock(accountId).orElseThrow().lastUsedTimeStep())
      .isNull();
  }

  @Test
  void shouldReturnEmptyForUnknownAccount() {
    assertThat(mfa.lock(UUID.randomUUID())).isEmpty();
  }
}
