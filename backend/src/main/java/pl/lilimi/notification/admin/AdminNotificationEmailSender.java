package pl.lilimi.notification.admin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AdminNotificationEmailSender {

  private final ObjectProvider<JavaMailSender> mailSender;
  private final AdminNotificationProperties properties;
  private final String from;

  public AdminNotificationEmailSender(
    ObjectProvider<JavaMailSender> mailSender,
    AdminNotificationProperties properties,
    @Value("${lilimi.mail.from:contact@lilimistudio.com}")
    String from
  ) {
    this.mailSender = mailSender;
    this.properties = properties;
    this.from = from;
  }

  public void send(AdminNotificationMessage notification) {
    Objects.requireNonNull(notification, "Notification is required");

    var email = properties.email();

    if (!email.enabled()) {
      throw new AdminNotificationDeliveryException(
        "ADMIN_EMAIL_DISABLED",
        false
      );
    }

    var sender = mailSender.getIfAvailable();

    if (sender == null) {
      throw new AdminNotificationDeliveryException(
        "SMTP_UNAVAILABLE",
        true
      );
    }

    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email.recipient());
    message.setSubject(notification.subject());
    message.setText(notification.text());

    try {
      sender.send(message);
    } catch (MailAuthenticationException exception) {
      throw new AdminNotificationDeliveryException(
        "SMTP_AUTHENTICATION_FAILED",
        false
      );
    } catch (MailParseException exception) {
      throw new AdminNotificationDeliveryException(
        "SMTP_INVALID_MESSAGE",
        false
      );
    } catch (MailException exception) {
      throw new AdminNotificationDeliveryException(
        "SMTP_DELIVERY_FAILED",
        true
      );
    }
  }
}
