package pl.lilimi.security.turnstile;

public class TurnstileRejectedException extends RuntimeException {

  public TurnstileRejectedException() {
    super("Turnstile verification was rejected");
  }
}
