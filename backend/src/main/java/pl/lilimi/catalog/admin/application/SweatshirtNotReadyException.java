package pl.lilimi.catalog.admin.application;

public class SweatshirtNotReadyException extends RuntimeException {

  public SweatshirtNotReadyException() {
    super("A sweatshirt needs an active variant linked to an active embroidery pattern before publication.");
  }
}
