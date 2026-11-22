package pl.lilimi.auth.mfa;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AdminMfaRepository {

  private final JdbcTemplate jdbc;

  public AdminMfaRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<MfaState> lock(UUID accountId) {
    return jdbc.query("""
      SELECT encrypted_secret, enabled_at, last_used_time_step
      FROM admin_mfa
      WHERE account_id = ?
      FOR UPDATE
      """,
      (result, rowNumber) -> {
        OffsetDateTime enabledAt = result.getObject(
          "enabled_at",
          OffsetDateTime.class
        );

        return new MfaState(
          result.getString("encrypted_secret"),
          enabledAt == null ? null : enabledAt.toInstant(),
          result.getObject("last_used_time_step", Long.class)
        );
      },
      accountId
    ).stream().findFirst();
  }

  public boolean createPending(
    UUID accountId,
    String encryptedSecret
  ) {
    return jdbc.update("""
      INSERT INTO admin_mfa (account_id, encrypted_secret)
      VALUES (?, ?)
      ON CONFLICT (account_id) DO NOTHING
      """,
      accountId,
      encryptedSecret
    ) == 1;
  }

  public boolean enable(UUID accountId, long timeStep) {
    return jdbc.update("""
      UPDATE admin_mfa
      SET enabled_at = CURRENT_TIMESTAMP,
          last_used_time_step = ?
      WHERE account_id = ?
        AND enabled_at IS NULL
      """,
      timeStep,
      accountId
    ) == 1;
  }

  public boolean consumeTimeStep(UUID accountId, long timeStep) {
    return jdbc.update("""
      UPDATE admin_mfa
      SET last_used_time_step = ?
      WHERE account_id = ?
        AND enabled_at IS NOT NULL
        AND (
          last_used_time_step IS NULL
          OR last_used_time_step < ?
        )
      """,
      timeStep,
      accountId,
      timeStep
    ) == 1;
  }

  public void addRecoveryCode(UUID accountId, String codeHash) {
    jdbc.update("""
    INSERT INTO admin_mfa_recovery_codes (
      id,
      account_id,
      code_hash
    )
    VALUES (?, ?, ?)
    """,
      UUID.randomUUID(),
      accountId,
      codeHash
    );
  }

  public List<StoredRecoveryCode> lockUnusedRecoveryCodes(
    UUID accountId
  ) {
    return jdbc.query("""
    SELECT id, code_hash
    FROM admin_mfa_recovery_codes
    WHERE account_id = ?
      AND used_at IS NULL
    ORDER BY id
    FOR UPDATE
    """,
      (result, rowNumber) -> new StoredRecoveryCode(
        result.getObject("id", UUID.class),
        result.getString("code_hash")
      ),
      accountId
    );
  }

  public boolean consumeRecoveryCode(UUID accountId, UUID codeId) {
    return jdbc.update("""
    UPDATE admin_mfa_recovery_codes
    SET used_at = CURRENT_TIMESTAMP
    WHERE id = ?
      AND account_id = ?
      AND used_at IS NULL
    """,
      codeId,
      accountId
    ) == 1;
  }

  public record StoredRecoveryCode(UUID id, String hash) {

    @Override
    public String toString() {
      return "StoredRecoveryCode[REDACTED]";
    }
  }

  public record MfaState(
    String encryptedSecret,
    Instant enabledAt,
    Long lastUsedTimeStep
  ) {
    public boolean enabled() {
      return enabledAt != null;
    }
  }
}
