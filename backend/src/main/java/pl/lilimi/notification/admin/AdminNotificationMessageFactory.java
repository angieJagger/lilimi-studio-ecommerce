package pl.lilimi.notification.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdminNotificationMessageFactory {

  private final ProjectInquiryRepository inquiries;
  private final OrderRepository orders;
  private final PaymentAttemptRepository payments;
  private final String publicBaseUrl;

  public AdminNotificationMessageFactory(
    ProjectInquiryRepository inquiries,
    OrderRepository orders,
    PaymentAttemptRepository payments,
    @Value("${lilimi.public-base-url:https://lilimistudio.com}")
    String publicBaseUrl
  ) {
    this.inquiries = inquiries;
    this.orders = orders;
    this.payments = payments;
    this.publicBaseUrl = validateBaseUrl(publicBaseUrl);
  }

  public Optional<AdminNotificationMessage> create(
    AdminNotificationRepository.Claim claim
  ) {
    return switch (claim.eventType()) {
      case "INQUIRY_CREATED" -> inquiry(claim.resourceId());
      case "ORDER_PAID" -> paidOrder(claim.resourceId());
      default -> throw new AdminNotificationDeliveryException(
        "UNKNOWN_EVENT_TYPE",
        false
      );
    };
  }

  private Optional<AdminNotificationMessage> inquiry(UUID inquiryId) {
    if (!inquiries.existsById(inquiryId)) {
      return Optional.empty();
    }

    String link = publicBaseUrl + "/pl/admin/inquiries/" + inquiryId;

    return Optional.of(new AdminNotificationMessage(
      "Lilimi Studio — nowe zapytanie",
      """
      Nowe zapytanie projektowe w Lilimi Studio.

      Numer: %s

      Szczegóły w panelu administratora:
      %s
      """.formatted(inquiryId, link)
    ));
  }

  private Optional<AdminNotificationMessage> paidOrder(UUID orderId) {
    var order = orders.findById(orderId);

    if (
      order.isEmpty()
        || order.get().getStatus() == OrderStatus.CANCELLED
        || !payments.existsByOrderIdAndStatus(
        orderId,
        PaymentAttemptStatus.SUCCEEDED
      )
    ) {
      return Optional.empty();
    }

    String amount = BigDecimal.valueOf(
      order.get().getTotalInGrosz(),
      2
    ).toPlainString();

    String link = publicBaseUrl + "/pl/admin/orders/" + orderId;

    return Optional.of(new AdminNotificationMessage(
      "Lilimi Studio — opłacone zamówienie",
      """
      Potwierdzono płatność za zamówienie w Lilimi Studio.

      Numer: %s
      Kwota: %s %s

      Szczegóły w panelu administratora:
      %s
      """.formatted(orderId, amount, order.get().getCurrency(), link)
    ));
  }

  private static String validateBaseUrl(String value) {
    URI uri = URI.create(value.trim());

    boolean https = "https".equalsIgnoreCase(uri.getScheme());
    boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
      && (
      "localhost".equalsIgnoreCase(uri.getHost())
        || "127.0.0.1".equals(uri.getHost())
    );

    if (
      (!https && !localHttp)
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || (
        uri.getPath() != null
          && !uri.getPath().isEmpty()
          && !uri.getPath().equals("/")
      )
    ) {
      throw new IllegalArgumentException(
        "Configure a valid public base URL for notifications"
      );
    }

    return uri.toString().replaceAll("/+$", "");
  }
}
