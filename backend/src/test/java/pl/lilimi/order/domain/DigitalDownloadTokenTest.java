package pl.lilimi.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DigitalDownloadTokenTest {

  private static final Instant CREATED_AT =
    Instant.parse("2026-11-01T10:00:00Z");

  private static final Instant EXPIRES_AT =
    CREATED_AT.plus(7, ChronoUnit.DAYS);

  @Test
  void shouldBeUsableFromCreationUntilExpiry() {
    var token = createToken();

    assertThat(token.isUsableAt(CREATED_AT.minusNanos(1)))
      .isFalse();

    assertThat(token.isUsableAt(CREATED_AT))
      .isTrue();

    assertThat(token.isUsableAt(EXPIRES_AT.minusNanos(1)))
      .isTrue();

    assertThat(token.isUsableAt(EXPIRES_AT))
      .isFalse();
  }

  @Test
  void shouldRejectAccessAfterRevocation() {
    var token = createToken();
    Instant revokedAt = CREATED_AT.plusSeconds(60);

    token.revoke(revokedAt);

    assertThat(token.isUsableAt(revokedAt)).isFalse();
    assertThat(token.isUsableAt(EXPIRES_AT.minusSeconds(1)))
      .isFalse();
  }

  @Test
  void shouldAllowRepeatedRevocation() {
    var token = createToken();

    token.revoke(CREATED_AT.plusSeconds(60));
    token.revoke(CREATED_AT.plusSeconds(120));

    assertThat(token.isUsableAt(CREATED_AT.plusSeconds(120)))
      .isFalse();
  }

  @Test
  void shouldRejectRevocationBeforeCreation() {
    var token = createToken();

    assertThatIllegalArgumentException()
      .isThrownBy(() ->
        token.revoke(CREATED_AT.minusSeconds(1))
      );

    assertThat(token.isUsableAt(CREATED_AT)).isTrue();
  }

  @Test
  void shouldRejectInvalidHash() {
    assertThatIllegalArgumentException()
      .isThrownBy(() -> new DigitalDownloadToken(
        UUID.randomUUID(),
        "invalid",
        CREATED_AT,
        EXPIRES_AT
      ));
  }

  @Test
  void shouldRejectExpiryAtOrBeforeCreation() {
    for (Instant expiry : new Instant[] {
      CREATED_AT,
      CREATED_AT.minusSeconds(1),
    }) {
      assertThatIllegalArgumentException()
        .isThrownBy(() -> new DigitalDownloadToken(
          UUID.randomUUID(),
          "a".repeat(64),
          CREATED_AT,
          expiry
        ));
    }
  }

  private DigitalDownloadToken createToken() {
    return new DigitalDownloadToken(
      UUID.randomUUID(),
      "a".repeat(64),
      CREATED_AT,
      EXPIRES_AT
    );
  }
}
