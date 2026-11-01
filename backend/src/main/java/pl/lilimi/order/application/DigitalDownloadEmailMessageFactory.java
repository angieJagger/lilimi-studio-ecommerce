package pl.lilimi.order.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pl.lilimi.order.domain.Order;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class DigitalDownloadEmailMessageFactory {

  private static final DateTimeFormatter EXPIRY_FORMAT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
      .withZone(ZoneOffset.UTC);

  private final String publicBaseUrl;

  public DigitalDownloadEmailMessageFactory(
    @Value("${lilimi.public-base-url:https://lilimistudio.com}")
    String publicBaseUrl
  ) {
    this.publicBaseUrl = validateBaseUrl(publicBaseUrl);
  }

  public DigitalDownloadEmailMessage create(
    Order order,
    IssuedDigitalDownloadToken issued
  ) {
    Objects.requireNonNull(order);
    Objects.requireNonNull(issued);

    if (!issued.orderId().equals(order.getId())) {
      throw new IllegalArgumentException(
        "Download token belongs to another order"
      );
    }

    if (!issued.rawToken().matches("[A-Za-z0-9_-]{43}")) {
      throw new IllegalArgumentException(
        "Invalid digital download token"
      );
    }

    boolean polish = "pl".equals(order.getLanguage());
    String language = polish ? "pl" : "en";

    String link = publicBaseUrl
      + "/"
      + language
      + "/downloads#token="
      + issued.rawToken();

    String expiresAt = EXPIRY_FORMAT.format(issued.expiresAt());

    String subject = polish
      ? "Lilimi Studio — pliki do pobrania"
      : "Lilimi Studio — your downloads";

    String text = polish
      ? """
        Płatność za zamówienie %s została potwierdzona.

        Zakupione pliki cyfrowe są dostępne pod linkiem:
        %s

        Link jest ważny do: %s.
        Do tego czasu można pobierać pliki wielokrotnie.

        Link umożliwia dostęp do zakupionych plików.
        Nie udostępniaj go innym osobom.

        Jeśli zamówienie złożono po zalogowaniu, pliki są również
        dostępne w szczegółach zamówienia na koncie Lilimi Studio.

        Kontakt: contact@lilimistudio.com

        Lilimi Studio
        """.formatted(order.getId(), link, expiresAt)
      : """
        Payment for order %s has been confirmed.

        Your purchased digital files are available here:
        %s

        This link is valid until: %s.
        You can download the files multiple times before it expires.

        This link grants access to your purchased files.
        Please do not share it with others.

        If you placed the order while signed in, the files are also
        available in the order details in your Lilimi Studio account.

        Contact: contact@lilimistudio.com

        Lilimi Studio
        """.formatted(order.getId(), link, expiresAt);

    return new DigitalDownloadEmailMessage(
      order.getContact().getEmail(),
      subject,
      text
    );
  }

  private static String validateBaseUrl(String value) {
    Objects.requireNonNull(value);

    URI uri = URI.create(value);

    boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
      && (
      "localhost".equalsIgnoreCase(uri.getHost())
        || "127.0.0.1".equals(uri.getHost())
    );

    if (
      uri.getHost() == null
        || !(
        "https".equalsIgnoreCase(uri.getScheme())
          || localHttp
      )
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !(
        uri.getPath().isEmpty()
          || "/".equals(uri.getPath())
      )
    ) {
      throw new IllegalArgumentException(
        "Invalid public base URL"
      );
    }

    return value.replaceAll("/+$", "");
  }
}
