package pl.lilimi.notification.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lilimi.notifications.admin")
public record AdminNotificationProperties(
  @DefaultValue Telegram telegram,
  @DefaultValue Email email
) {

  public record Telegram(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String botToken,
    @DefaultValue("") String chatId
  ) {

    public Telegram {
      botToken = botToken == null ? "" : botToken.trim();
      chatId = chatId == null ? "" : chatId.trim();

      if (enabled && botToken.isBlank()) {
        throw new IllegalArgumentException(
          "Configure the administrator Telegram bot token"
        );
      }

      if (enabled && !chatId.matches("-?[0-9]+")) {
        throw new IllegalArgumentException(
          "Configure a valid administrator Telegram chat ID"
        );
      }
    }

    @Override
    public String toString() {
      return "Telegram[enabled=" + enabled + ", credentials=REDACTED]";
    }
  }

  public record Email(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String recipient
  ) {

    public Email {
      recipient = recipient == null ? "" : recipient.trim();

      if (enabled && recipient.isBlank()) {
        throw new IllegalArgumentException(
          "Configure the administrator notification email"
        );
      }

      if (recipient.contains("\r") || recipient.contains("\n")) {
        throw new IllegalArgumentException(
          "Invalid administrator notification email"
        );
      }
    }

    @Override
    public String toString() {
      return "Email[enabled=" + enabled + ", recipient=REDACTED]";
    }
  }
}
