package pl.lilimi.auth.api;

public record ResetPasswordRequest(
  String token,
  String password
) {

  @Override
  public String toString() {
    return "ResetPasswordRequest[credentials=REDACTED]";
  }
}
