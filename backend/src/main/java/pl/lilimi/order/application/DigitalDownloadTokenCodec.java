package pl.lilimi.order.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class DigitalDownloadTokenCodec {

  private static final int TOKEN_BYTES = 32;

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);

    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(bytes);
  }

  public String hash(String rawToken) {
    if (
      rawToken == null
        || !rawToken.matches("[A-Za-z0-9_-]{43}")
    ) {
      throw new IllegalArgumentException(
        "Invalid digital download token"
      );
    }

    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(rawToken.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
        "SHA-256 is unavailable",
        exception
      );
    }
  }
}
