package pl.lilimi.auth.mfa;

import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

import static java.nio.charset.StandardCharsets.US_ASCII;

@Service
public class MfaTotpService {

  private static final long PERIOD_SECONDS = 30;

  private final DefaultSecretGenerator secrets =
    new DefaultSecretGenerator();

  private final DefaultCodeGenerator codes =
    new DefaultCodeGenerator();

  public String generateSecret() {
    return secrets.generate();
  }

  public OptionalLong matchingTimeStep(
    String secret,
    String code,
    Instant now
  ) {
    if (code == null || !code.matches("[0-9]{6}")) {
      return OptionalLong.empty();
    }

    long currentStep = Math.floorDiv(
      now.getEpochSecond(),
      PERIOD_SECONDS
    );

    try {
      for (long step = currentStep + 1; step >= currentStep - 1; step--) {
        if (step < 0) {
          continue;
        }

        String expected = codes.generate(secret, step);

        if (MessageDigest.isEqual(
          expected.getBytes(US_ASCII),
          code.getBytes(US_ASCII)
        )) {
          return OptionalLong.of(step);
        }
      }

      return OptionalLong.empty();
    } catch (CodeGenerationException exception) {
      throw new IllegalStateException(
        "Could not verify MFA code",
        exception
      );
    }
  }
}
