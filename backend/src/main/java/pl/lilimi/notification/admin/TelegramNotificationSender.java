package pl.lilimi.notification.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Service
public class TelegramNotificationSender {

  private final AdminNotificationProperties properties;
  private final RestClient client;

  @Autowired
  public TelegramNotificationSender(
    AdminNotificationProperties properties
  ) {
    this(properties, createClient());
  }

  TelegramNotificationSender(
    AdminNotificationProperties properties,
    RestClient client
  ) {
    this.properties = properties;
    this.client = client;
  }

  public void send(String text) {
    var telegram = properties.telegram();

    if (!telegram.enabled()) {
      throw new AdminNotificationDeliveryException(
        "TELEGRAM_DISABLED",
        false
      );
    }

    try {
      var response = client.post()
        .uri("/bot{token}/sendMessage", telegram.botToken())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of(
          "chat_id", telegram.chatId(),
          "text", text,
          "link_preview_options", Map.of("is_disabled", true)
        ))
        .retrieve()
        .body(TelegramResponse.class);

      if (response == null || !response.ok()) {
        throw new AdminNotificationDeliveryException(
          "TELEGRAM_INVALID_RESPONSE",
          true
        );
      }
    } catch (RestClientResponseException exception) {
      int status = exception.getStatusCode().value();

      throw new AdminNotificationDeliveryException(
        status == 429
          ? "TELEGRAM_RATE_LIMITED"
          : "TELEGRAM_HTTP_" + status,
        status == 429 || status >= 500
      );
    } catch (RestClientException exception) {
      throw new AdminNotificationDeliveryException(
        "TELEGRAM_CONNECTION_FAILED",
        true
      );
    }
  }

  private static RestClient createClient() {
    var httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

    var factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(10));

    return RestClient.builder()
      .baseUrl("https://api.telegram.org")
      .requestFactory(factory)
      .build();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TelegramResponse(boolean ok) {
  }
}
