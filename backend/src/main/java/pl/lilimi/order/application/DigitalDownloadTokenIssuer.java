package pl.lilimi.order.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.order.domain.DigitalDownloadToken;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.DigitalDownloadTokenRepository;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Service
public class DigitalDownloadTokenIssuer {

  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final PaymentAttemptRepository payments;
  private final DigitalDownloadTokenRepository tokens;
  private final DigitalDownloadTokenCodec codec;
  private final Clock clock;
  private final Duration validity;

  public DigitalDownloadTokenIssuer(
    OrderRepository orders,
    OrderItemRepository items,
    PaymentAttemptRepository payments,
    DigitalDownloadTokenRepository tokens,
    DigitalDownloadTokenCodec codec,
    Clock clock,
    @Value("${lilimi.digital-downloads.token-validity:P7D}")
    Duration validity
  ) {
    this.orders = orders;
    this.items = items;
    this.payments = payments;
    this.tokens = tokens;
    this.codec = codec;
    this.clock = clock;
    this.validity = Objects.requireNonNull(validity);

    if (validity.isZero() || validity.isNegative()) {
      throw new IllegalArgumentException(
        "Download token validity must be positive"
      );
    }
  }

  @Transactional
  public IssuedDigitalDownloadToken issue(UUID orderId) {
    Objects.requireNonNull(orderId);

    var order = orders.findByIdForUpdate(orderId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "ORDER_NOT_FOUND"
      ));

    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_CANCELLED"
      );
    }

    if (!payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_NOT_PAID"
      );
    }

    if (!items.existsByOrder_IdAndKindAndDigitalPackageIdIsNotNull(
      orderId,
      "digital"
    )) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "DIGITAL_DOWNLOAD_NOT_AVAILABLE"
      );
    }

    var createdAt = clock.instant();
    var expiresAt = createdAt.plus(validity);
    String rawToken = codec.generate();

    tokens.save(new DigitalDownloadToken(
      orderId,
      codec.hash(rawToken),
      createdAt,
      expiresAt
    ));

    return new IssuedDigitalDownloadToken(
      orderId,
      rawToken,
      expiresAt
    );
  }
}
