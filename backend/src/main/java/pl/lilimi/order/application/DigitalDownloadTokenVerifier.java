package pl.lilimi.order.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.order.persistence.DigitalDownloadTokenRepository;

import java.time.Clock;
import java.util.UUID;

@Service
public class DigitalDownloadTokenVerifier {

  private final DigitalDownloadTokenRepository tokens;
  private final DigitalDownloadTokenCodec codec;
  private final Clock clock;

  public DigitalDownloadTokenVerifier(
    DigitalDownloadTokenRepository tokens,
    DigitalDownloadTokenCodec codec,
    Clock clock
  ) {
    this.tokens = tokens;
    this.codec = codec;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public UUID requireOrderId(String rawToken) {
    String hash;

    try {
      hash = codec.hash(rawToken);
    } catch (IllegalArgumentException exception) {
      throw invalidToken();
    }

    var token = tokens.findByTokenHash(hash)
      .orElseThrow(DigitalDownloadTokenVerifier::invalidToken);

    if (!token.isUsableAt(clock.instant())) {
      throw invalidToken();
    }

    return token.getOrderId();
  }

  private static ResponseStatusException invalidToken() {
    return new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "DIGITAL_DOWNLOAD_LINK_INVALID"
    );
  }
}
