package pl.lilimi.auth.mfa;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class MfaRecoveryCodeService {

  private static final int CODE_COUNT = 10;
  private static final int CODE_BYTES = 16;

  private final SecureRandom random = new SecureRandom();
  private final PasswordEncoder passwordEncoder;

  public MfaRecoveryCodeService(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  public List<RecoveryCode> generate() {
    var result = new ArrayList<RecoveryCode>(CODE_COUNT);

    for (int index = 0; index < CODE_COUNT; index++) {
      byte[] bytes = new byte[CODE_BYTES];
      random.nextBytes(bytes);

      String normalized = HexFormat.of().formatHex(bytes);
      String displayed = String.join(
        "-",
        normalized.substring(0, 8),
        normalized.substring(8, 16),
        normalized.substring(16, 24),
        normalized.substring(24, 32)
      );

      result.add(new RecoveryCode(
        displayed,
        passwordEncoder.encode(normalized)
      ));
    }

    return List.copyOf(result);
  }

  public boolean matches(String submittedCode, String hash) {
    if (submittedCode == null || submittedCode.length() > 64) {
      return false;
    }

    String normalized = submittedCode
      .strip()
      .replace("-", "")
      .toLowerCase(Locale.ROOT);

    if (!normalized.matches("[0-9a-f]{32}")) {
      return false;
    }

    return passwordEncoder.matches(normalized, hash);
  }

  public record RecoveryCode(String value, String hash) {

    @Override
    public String toString() {
      return "RecoveryCode[REDACTED]";
    }
  }
}
