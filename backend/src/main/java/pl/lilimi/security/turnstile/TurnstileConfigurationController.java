package pl.lilimi.security.turnstile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security/turnstile")
public class TurnstileConfigurationController {

  private final TurnstileProperties properties;
  private final String siteKey;

  public TurnstileConfigurationController(
    TurnstileProperties properties,
    @Value("${lilimi.security.turnstile.site-key:}")
    String siteKey
  ) {
    this.properties = properties;
    this.siteKey = siteKey.strip();

    if (properties.isEnabled() && this.siteKey.isEmpty()) {
      throw new IllegalArgumentException(
        "Turnstile site key is required when protection is enabled"
      );
    }
  }

  @GetMapping
  public ResponseEntity<TurnstileConfigurationResponse> configuration() {
    var response = new TurnstileConfigurationResponse(
      properties.isEnabled(),
      properties.isEnabled() ? siteKey : null
    );

    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(response);
  }

  public record TurnstileConfigurationResponse(
    boolean enabled,
    String siteKey
  ) {
  }
}
