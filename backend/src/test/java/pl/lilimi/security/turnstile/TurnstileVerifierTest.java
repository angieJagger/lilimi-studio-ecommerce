package pl.lilimi.security.turnstile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnstileVerifierTest {

  private static final String TOKEN = "test-only-token";
  private static final String ACTION = "project-inquiry";

  @Mock
  private TurnstileClient client;

  private TurnstileVerifier verifier;

  @BeforeEach
  void setUp() {
    verifier = new TurnstileVerifier(
      new TurnstileProperties(
        true,
        "test-only-secret",
        "lilimistudio.com"
      ),
      client
    );
  }

  @Test
  void shouldSkipVerificationWhenDisabled() {
    var disabledVerifier = new TurnstileVerifier(
      new TurnstileProperties(false, "", ""),
      client
    );

    assertThatCode(() -> disabledVerifier.verify(null, ACTION))
      .doesNotThrowAnyException();

    verifyNoInteractions(client);
  }

  @Test
  void shouldRejectMissingBlankAndOversizedTokensWithoutCallingCloudflare() {
    for (String token : new String[] { null, "", " ", "x".repeat(2049) }) {
      assertThatThrownBy(() -> verifier.verify(token, ACTION))
        .isInstanceOf(TurnstileRejectedException.class);
    }

    verifyNoInteractions(client);
  }

  @Test
  void shouldAcceptSuccessfulVerificationForExpectedHostnameAndAction() {
    when(client.verify(TOKEN)).thenReturn(
      response(true, "lilimistudio.com", ACTION)
    );

    assertThatCode(() -> verifier.verify(TOKEN, ACTION))
      .doesNotThrowAnyException();

    verify(client).verify(TOKEN);
  }

  @Test
  void shouldRejectSuccessfulTokenFromAnotherHostname() {
    when(client.verify(TOKEN)).thenReturn(
      response(true, "other.example", ACTION)
    );

    assertThatThrownBy(() -> verifier.verify(TOKEN, ACTION))
      .isInstanceOf(TurnstileRejectedException.class);
  }

  @Test
  void shouldRejectSuccessfulTokenForAnotherAction() {
    when(client.verify(TOKEN)).thenReturn(
      response(true, "lilimistudio.com", "registration")
    );

    assertThatThrownBy(() -> verifier.verify(TOKEN, ACTION))
      .isInstanceOf(TurnstileRejectedException.class);
  }

  @Test
  void shouldRejectExpiredOrAlreadyUsedToken() {
    when(client.verify(TOKEN)).thenReturn(
      new TurnstileVerificationResponse(
        false,
        null,
        null,
        List.of("timeout-or-duplicate")
      )
    );

    assertThatThrownBy(() -> verifier.verify(TOKEN, ACTION))
      .isInstanceOf(TurnstileRejectedException.class);
  }

  @Test
  void shouldTreatInvalidSecretAsServiceUnavailable() {
    when(client.verify(TOKEN)).thenReturn(
      new TurnstileVerificationResponse(
        false,
        null,
        null,
        List.of("invalid-input-secret")
      )
    );

    assertThatThrownBy(() -> verifier.verify(TOKEN, ACTION))
      .isInstanceOf(TurnstileUnavailableException.class);
  }

  @Test
  void shouldPropagateServiceUnavailability() {
    when(client.verify(TOKEN))
      .thenThrow(new TurnstileUnavailableException());

    assertThatThrownBy(() -> verifier.verify(TOKEN, ACTION))
      .isInstanceOf(TurnstileUnavailableException.class);
  }

  private TurnstileVerificationResponse response(
    boolean success,
    String hostname,
    String action
  ) {
    return new TurnstileVerificationResponse(
      success,
      hostname,
      action,
      List.of()
    );
  }
}
