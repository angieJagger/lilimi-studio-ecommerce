package pl.lilimi.order.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DigitalDownloadTokenCodecTest {

  private final DigitalDownloadTokenCodec codec =
    new DigitalDownloadTokenCodec();

  @Test
  void shouldGenerateUrlSafeToken() {
    String token = codec.generate();

    assertThat(token).matches("[A-Za-z0-9_-]{43}");
  }

  @Test
  void shouldGenerateDifferentTokens() {
    String first = codec.generate();
    String second = codec.generate();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void shouldHashTokenUsingSha256() throws Exception {
    String token = "a".repeat(43);

    byte[] expectedDigest = MessageDigest
      .getInstance("SHA-256")
      .digest(token.getBytes(StandardCharsets.UTF_8));

    String expectedHash = HexFormat.of()
      .formatHex(expectedDigest);

    assertThat(codec.hash(token)).isEqualTo(expectedHash);
    assertThat(codec.hash(token)).matches("[0-9a-f]{64}");
  }

  @Test
  void shouldRejectMissingToken() {
    assertThatIllegalArgumentException()
      .isThrownBy(() -> codec.hash(null));
  }

  @Test
  void shouldRejectMalformedTokens() {
    String[] invalidTokens = {
      "",
      "a".repeat(42),
      "a".repeat(44),
      "a".repeat(42) + "/",
      "a".repeat(42) + "=",
      "a".repeat(42) + " ",
    };

    for (String token : invalidTokens) {
      assertThatIllegalArgumentException()
        .as("Token: %s", token)
        .isThrownBy(() -> codec.hash(token));
    }
  }
}
