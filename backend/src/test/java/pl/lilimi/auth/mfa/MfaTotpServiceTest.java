package pl.lilimi.auth.mfa;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MfaTotpServiceTest {

  private static final String SECRET =
    "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  private final MfaTotpService service = new MfaTotpService();

  @Test
  void shouldAcceptKnownCodeAndReturnItsTimeStep() {
    var result = service.matchingTimeStep(
      SECRET,
      "287082",
      Instant.ofEpochSecond(59)
    );

    assertThat(result).hasValue(1);
  }

  @Test
  void shouldAcceptCodeFromPreviousTimeStep() {
    var result = service.matchingTimeStep(
      SECRET,
      "287082",
      Instant.ofEpochSecond(60)
    );

    assertThat(result).hasValue(1);
  }

  @Test
  void shouldAcceptCodeFromNextTimeStep() {
    var result = service.matchingTimeStep(
      SECRET,
      "287082",
      Instant.ofEpochSecond(29)
    );

    assertThat(result).hasValue(1);
  }

  @Test
  void shouldRejectCodeOutsideAllowedTimeWindow() {
    var result = service.matchingTimeStep(
      SECRET,
      "287082",
      Instant.ofEpochSecond(90)
    );

    assertThat(result).isEmpty();
  }

  @Test
  void shouldRejectIncorrectCode() {
    assertThat(service.matchingTimeStep(
      SECRET,
      "000000",
      Instant.ofEpochSecond(59)
    )).isEmpty();
  }

  @Test
  void shouldRejectMalformedCodes() {
    for (String code : new String[]{
      "", "12345", "1234567", "abcdef", " 287082"
    }) {
      assertThat(service.matchingTimeStep(
        SECRET,
        code,
        Instant.ofEpochSecond(59)
      )).isEmpty();
    }

    assertThat(service.matchingTimeStep(
      SECRET,
      null,
      Instant.ofEpochSecond(59)
    )).isEmpty();
  }

  @Test
  void shouldGenerateDifferentBase32Secrets() {
    String first = service.generateSecret();
    String second = service.generateSecret();

    assertThat(first).matches("[A-Z2-7]{32}");
    assertThat(second).matches("[A-Z2-7]{32}");
    assertThat(first).isNotEqualTo(second);
  }
}
