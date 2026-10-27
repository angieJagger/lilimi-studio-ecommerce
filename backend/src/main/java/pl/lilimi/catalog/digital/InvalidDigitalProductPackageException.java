package pl.lilimi.catalog.digital;

public class InvalidDigitalProductPackageException
  extends RuntimeException {

  public InvalidDigitalProductPackageException(String message) {
    super(message);
  }

  public InvalidDigitalProductPackageException(
    String message,
    Throwable cause
  ) {
    super(message, cause);
  }
}
