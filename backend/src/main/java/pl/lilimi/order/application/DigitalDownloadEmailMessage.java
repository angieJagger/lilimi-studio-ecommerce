package pl.lilimi.order.application;

import java.util.Objects;

public record DigitalDownloadEmailMessage(
  String recipient,
  String subject,
  String text
) {

  public DigitalDownloadEmailMessage {
    Objects.requireNonNull(recipient);
    Objects.requireNonNull(subject);
    Objects.requireNonNull(text);

    if (recipient.isBlank() || subject.isBlank() || text.isBlank()) {
      throw new IllegalArgumentException(
        "Download email fields must not be blank"
      );
    }

    if (
      recipient.contains("\r")
        || recipient.contains("\n")
        || subject.contains("\r")
        || subject.contains("\n")
    ) {
      throw new IllegalArgumentException(
        "Download email headers must not contain line breaks"
      );
    }
  }

  @Override
  public String toString() {
    return "DigitalDownloadEmailMessage[content=[REDACTED]]";
  }
}
