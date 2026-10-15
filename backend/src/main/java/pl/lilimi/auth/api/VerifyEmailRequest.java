package pl.lilimi.auth.api;

public record VerifyEmailRequest(String token) {

  @Override
  public String toString() {
    return "VerifyEmailRequest[token=REDACTED]";
  }
}
