package pl.lilimi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

  private final PasswordEncoder encoder =
    PasswordEncoderFactories.createDelegatingPasswordEncoder();

  @Test
  void shouldEncodeAndVerifyPassword() {
    String password = "Test-only-password-2026!";
    String encoded = encoder.encode(password);

    assertThat(encoded).isNotEqualTo(password);
    assertThat(encoded).startsWith("{bcrypt}");
    assertThat(encoder.matches(password, encoded)).isTrue();
    assertThat(encoder.matches("Wrong-password", encoded)).isFalse();
  }

  @Test
  void shouldGenerateDifferentHashesForTheSamePassword() {
    String password = "Test-only-password-2026!";

    String first = encoder.encode(password);
    String second = encoder.encode(password);

    assertThat(first).isNotEqualTo(second);
    assertThat(encoder.matches(password, first)).isTrue();
    assertThat(encoder.matches(password, second)).isTrue();
  }
}
