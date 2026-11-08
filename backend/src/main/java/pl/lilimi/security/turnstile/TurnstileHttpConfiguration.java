package pl.lilimi.security.turnstile;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class TurnstileHttpConfiguration {

  @Bean
  public RestClient turnstileRestClient() {
    var httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(2))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(5));

    return RestClient.builder()
      .baseUrl("https://challenges.cloudflare.com")
      .requestFactory(requestFactory)
      .build();
  }
}
