package pl.lilimi.security.turnstile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnstilePropertiesTest {

  @Test
  void shouldAllowDisabledProtectionWithoutCredentials() {
    var properties = new TurnstileProperties(false, "", "");

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.acceptsHostname("lilimistudio.com")).isFalse();
  }

  @Test
  void shouldRequireSecretWhenEnabled() {
    assertThatThrownBy(() ->
      new TurnstileProperties(true, " ", "lilimistudio.com")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
        "Turnstile secret key is required when protection is enabled"
      );
  }

  @Test
  void shouldRequireAllowedHostnamesWhenEnabled() {
    assertThatThrownBy(() ->
      new TurnstileProperties(true, "test-only-secret", " , ")
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
        "Turnstile requires at least one allowed hostname"
      );
  }

  @Test
  void shouldNormalizeConfiguredHostnames() {
    var properties = new TurnstileProperties(
      true,
      "test-only-secret",
      " LilimiStudio.com, localhost, 127.0.0.1 "
    );

    assertThat(properties.acceptsHostname("lilimistudio.com")).isTrue();
    assertThat(properties.acceptsHostname("LILIMISTUDIO.COM")).isTrue();
    assertThat(properties.acceptsHostname("localhost")).isTrue();
    assertThat(properties.acceptsHostname("127.0.0.1")).isTrue();
  }

  @Test
  void shouldRejectMissingAndUnlistedHostnames() {
    var properties = new TurnstileProperties(
      true,
      "test-only-secret",
      "lilimistudio.com"
    );

    assertThat(properties.acceptsHostname(null)).isFalse();
    assertThat(properties.acceptsHostname("")).isFalse();
    assertThat(properties.acceptsHostname("other.example")).isFalse();
    assertThat(properties.acceptsHostname("lilimistudio.com.other.example"))
      .isFalse();
  }
}
