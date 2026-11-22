package pl.lilimi.auth.mfa;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaSecretCipherTest {

  @Test
  void shouldDecryptEncryptedSecret() {
    var cipher = new MfaSecretCipher(randomKey());
    String secret = "JBSWY3DPEHPK3PXP";

    String encrypted = cipher.encrypt(secret);

    assertThat(encrypted).isNotEqualTo(secret);
    assertThat(cipher.decrypt(encrypted)).isEqualTo(secret);
  }

  @Test
  void shouldUseDifferentNonceForEachEncryption() {
    var cipher = new MfaSecretCipher(randomKey());

    String first = cipher.encrypt("JBSWY3DPEHPK3PXP");
    String second = cipher.encrypt("JBSWY3DPEHPK3PXP");

    assertThat(first).isNotEqualTo(second);
    assertThat(cipher.decrypt(first))
      .isEqualTo(cipher.decrypt(second));
  }

  @Test
  void shouldRejectTamperedPayload() {
    var cipher = new MfaSecretCipher(randomKey());
    byte[] payload = Base64.getDecoder().decode(
      cipher.encrypt("JBSWY3DPEHPK3PXP")
    );
    payload[payload.length - 1] ^= 1;

    String tampered = Base64.getEncoder().encodeToString(payload);

    assertThatThrownBy(() -> cipher.decrypt(tampered))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectDifferentKey() {
    var first = new MfaSecretCipher(randomKey());
    var second = new MfaSecretCipher(randomKey());
    String encrypted = first.encrypt("JBSWY3DPEHPK3PXP");

    assertThatThrownBy(() -> second.decrypt(encrypted))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectMissingKey() {
    var cipher = new MfaSecretCipher("");

    assertThatThrownBy(() -> cipher.encrypt("secret"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("32-byte");
  }

  private static String randomKey() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
