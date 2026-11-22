package pl.lilimi.auth.mfa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class MfaSecretCipher {

  private static final int NONCE_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecureRandom random = new SecureRandom();
  private final String configuredKey;

  public MfaSecretCipher(
    @Value("${lilimi.security.mfa.encryption-key:}") String configuredKey
  ) {
    this.configuredKey = configuredKey;
  }

  public String encrypt(String secret) {
    var key = encryptionKey();
    byte[] nonce = new byte[NONCE_LENGTH];
    random.nextBytes(nonce);

    try {
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
        Cipher.ENCRYPT_MODE,
        key,
        new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
      );

      byte[] encrypted = cipher.doFinal(secret.getBytes(UTF_8));
      byte[] payload = new byte[nonce.length + encrypted.length];

      System.arraycopy(nonce, 0, payload, 0, nonce.length);
      System.arraycopy(
        encrypted, 0, payload, nonce.length, encrypted.length
      );

      return Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
        "Could not encrypt MFA secret",
        exception
      );
    }
  }

  public String decrypt(String encodedPayload) {
    var key = encryptionKey();

    try {
      byte[] payload = Base64.getDecoder().decode(encodedPayload);

      if (payload.length <= NONCE_LENGTH + TAG_LENGTH_BITS / 8) {
        throw new IllegalArgumentException("Invalid MFA payload");
      }

      byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
      byte[] encrypted = Arrays.copyOfRange(
        payload, NONCE_LENGTH, payload.length
      );

      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
        Cipher.DECRYPT_MODE,
        key,
        new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
      );

      return new String(cipher.doFinal(encrypted), UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException(
        "Could not decrypt MFA secret",
        exception
      );
    }
  }

  private SecretKeySpec encryptionKey() {
    try {
      byte[] decoded = Base64.getDecoder().decode(configuredKey);

      if (decoded.length != 32) {
        throw new IllegalArgumentException("Expected 32-byte key");
      }

      return new SecretKeySpec(decoded, "AES");
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
        "Configure a Base64-encoded 32-byte MFA encryption key",
        exception
      );
    }
  }
}
