package pl.lilimi.security.turnstile;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class TurnstileVerifier {

  private static final int MAX_TOKEN_LENGTH = 2048;

  private static final Set<String> SERVICE_ERROR_CODES = Set.of(
    "missing-input-secret",
    "invalid-input-secret",
    "bad-request",
    "internal-error"
  );

  private final TurnstileProperties properties;
  private final TurnstileClient client;

  public TurnstileVerifier(
    TurnstileProperties properties,
    TurnstileClient client
  ) {
    this.properties = properties;
    this.client = client;
  }

  public void verify(String token, String expectedAction) {
    if (expectedAction == null || expectedAction.isBlank()) {
      throw new IllegalArgumentException(
        "Expected Turnstile action is required"
      );
    }

    if (!properties.isEnabled()) {
      return;
    }

    if (
      token == null
        || token.isBlank()
        || token.length() > MAX_TOKEN_LENGTH
    ) {
      throw new TurnstileRejectedException();
    }

    var response = client.verify(token);

    if (!response.isSuccessful()) {
      boolean serviceError = response.errorCodes() != null
        && response.errorCodes().stream()
        .anyMatch(SERVICE_ERROR_CODES::contains);

      if (serviceError) {
        throw new TurnstileUnavailableException();
      }

      throw new TurnstileRejectedException();
    }

    if (
      !properties.acceptsHostname(response.hostname())
        || !expectedAction.equals(response.action())
    ) {
      throw new TurnstileRejectedException();
    }
  }
}
