package pl.lilimi.order.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DigitalDownloadMailService {

  private final ObjectProvider<JavaMailSender> mailSender;
  private final String from;

  public DigitalDownloadMailService(
    ObjectProvider<JavaMailSender> mailSender,
    @Value("${lilimi.mail.from:contact@lilimistudio.com}")
    String from
  ) {
    this.mailSender = mailSender;
    this.from = from;
  }

  public void send(DigitalDownloadEmailMessage downloadMessage) {
    Objects.requireNonNull(downloadMessage);

    var sender = mailSender.getIfAvailable();

    if (sender == null) {
      throw new IllegalStateException(
        "DIGITAL_DOWNLOAD_SMTP_UNAVAILABLE"
      );
    }

    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(downloadMessage.recipient());
    message.setSubject(downloadMessage.subject());
    message.setText(downloadMessage.text());

    sender.send(message);
  }
}
