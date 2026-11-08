package pl.lilimi.security.turnstile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TurnstileProperties {

  private final boolean enabled;
  private final String secretKey;
  private final Set<String> allowedHostnames;

  public TurnstileProperties(
    @Value("${lilimi.security.turnstile.enabled:false}")
    boolean enabled,
    @Value("${lilimi.security.turnstile.secret-key:}")
    String secretKey,
    @Value("${lilimi.security.turnstile.allowed-hostnames:}")
    String allowedHostnames
  ) {
    this.enabled = enabled;
    this.secretKey = secretKey.strip();
    this.allowedHostnames = Arrays.stream(allowedHostnames.split(","))
      .map(String::strip)
      .filter(hostname -> !hostname.isEmpty())
      .map(hostname -> hostname.toLowerCase(Locale.ROOT))
      .collect(Collectors.toUnmodifiableSet());

    if (enabled && this.secretKey.isEmpty()) {
      throw new IllegalArgumentException(
        "Turnstile secret key is required when protection is enabled"
      );
    }

    if (enabled && this.allowedHostnames.isEmpty()) {
      throw new IllegalArgumentException(
        "Turnstile requires at least one allowed hostname"
      );
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public boolean acceptsHostname(String hostname) {
    return hostname != null
      && allowedHostnames.contains(
      hostname.strip().toLowerCase(Locale.ROOT)
    );
  }
}
