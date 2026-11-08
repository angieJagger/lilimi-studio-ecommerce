package pl.lilimi.security.turnstile;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TurnstileClient {

  private final RestClient client;
  private final TurnstileProperties properties;

  public TurnstileClient(
    @Qualifier("turnstileRestClient") RestClient client,
    TurnstileProperties properties
  ) {
    this.client = client;
    this.properties = properties;
  }

  public TurnstileVerificationResponse verify(String token) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("secret", properties.getSecretKey());
    form.add("response", token);

    TurnstileVerificationResponse response;

    try {
      response = client.post()
        .uri("/turnstile/v0/siteverify")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .accept(MediaType.APPLICATION_JSON)
        .body(form)
        .retrieve()
        .body(TurnstileVerificationResponse.class);
    } catch (RestClientException exception) {
      throw new TurnstileUnavailableException();
    }

    if (response == null || response.success() == null) {
      throw new TurnstileUnavailableException();
    }

    return response;
  }
}
