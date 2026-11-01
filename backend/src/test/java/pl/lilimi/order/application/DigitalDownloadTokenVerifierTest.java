package pl.lilimi.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.order.domain.DigitalDownloadToken;
import pl.lilimi.order.persistence.DigitalDownloadTokenRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalDownloadTokenVerifierTest {

  private static final UUID ORDER_ID = UUID.randomUUID();

  private static final Instant NOW =
    Instant.parse("2026-11-01T10:00:00Z");

  private static final String RAW_TOKEN = "a".repeat(43);

  @Mock
  private DigitalDownloadTokenRepository tokens;

  private final DigitalDownloadTokenCodec codec =
    new DigitalDownloadTokenCodec();

  private DigitalDownloadTokenVerifier verifier;

  @BeforeEach
  void setUp() {
    verifier = new DigitalDownloadTokenVerifier(
      tokens,
      codec,
      Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void shouldReturnOrderIdForValidToken() {
    var token = createToken(
      NOW.minusSeconds(60),
      NOW.plusSeconds(60)
    );

    prepareToken(token);

    assertThat(verifier.requireOrderId(RAW_TOKEN))
      .isEqualTo(ORDER_ID);

    verify(tokens).findByTokenHash(codec.hash(RAW_TOKEN));
  }

  @Test
  void shouldRejectMalformedTokenWithoutQueryingDatabase() {
    assertInvalid("invalid");

    verifyNoInteractions(tokens);
  }

  @Test
  void shouldRejectMissingTokenWithoutQueryingDatabase() {
    assertInvalid(null);

    verifyNoInteractions(tokens);
  }

  @Test
  void shouldRejectUnknownToken() {
    when(tokens.findByTokenHash(codec.hash(RAW_TOKEN)))
      .thenReturn(Optional.empty());

    assertInvalid(RAW_TOKEN);
  }

  @Test
  void shouldRejectTokenAtExactExpiry() {
    prepareToken(createToken(
      NOW.minusSeconds(60),
      NOW
    ));

    assertInvalid(RAW_TOKEN);
  }

  @Test
  void shouldRejectRevokedToken() {
    var token = createToken(
      NOW.minusSeconds(60),
      NOW.plusSeconds(60)
    );

    token.revoke(NOW.minusSeconds(30));
    prepareToken(token);

    assertInvalid(RAW_TOKEN);
  }

  @Test
  void shouldRejectTokenBeforeCreationTime() {
    prepareToken(createToken(
      NOW.plusSeconds(60),
      NOW.plusSeconds(120)
    ));

    assertInvalid(RAW_TOKEN);
  }

  private DigitalDownloadToken createToken(
    Instant createdAt,
    Instant expiresAt
  ) {
    return new DigitalDownloadToken(
      ORDER_ID,
      codec.hash(RAW_TOKEN),
      createdAt,
      expiresAt
    );
  }

  private void prepareToken(DigitalDownloadToken token) {
    when(tokens.findByTokenHash(codec.hash(RAW_TOKEN)))
      .thenReturn(Optional.of(token));
  }

  private void assertInvalid(String rawToken) {
    assertThatThrownBy(() -> verifier.requireOrderId(rawToken))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode().value())
            .isEqualTo(404);
          assertThat(exception.getReason())
            .isEqualTo("DIGITAL_DOWNLOAD_LINK_INVALID");
        }
      );
  }
}
