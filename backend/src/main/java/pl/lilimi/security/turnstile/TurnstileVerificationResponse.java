package pl.lilimi.security.turnstile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnstileVerificationResponse(
  Boolean success,
  String hostname,
  String action,
  @JsonProperty("error-codes")
  List<String> errorCodes
) {

  public boolean isSuccessful() {
    return Boolean.TRUE.equals(success);
  }
}
