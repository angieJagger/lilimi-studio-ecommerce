package pl.lilimi.catalog.admin.application;

public class DigitalProductNotReadyException extends RuntimeException {

  public DigitalProductNotReadyException() {
    super("A digital product needs Polish and English translations, a price, and a current ZIP package before publication.");
  }
}
