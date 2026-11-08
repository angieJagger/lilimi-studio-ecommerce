package pl.lilimi.security.turnstile;

public class TurnstileUnavailableException extends RuntimeException {

  public TurnstileUnavailableException() {
    super("Turnstile verification is temporarily unavailable");
  }
}
