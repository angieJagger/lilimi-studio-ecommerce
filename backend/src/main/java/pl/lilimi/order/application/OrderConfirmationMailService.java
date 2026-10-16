package pl.lilimi.order.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class OrderConfirmationMailService {

  private final ObjectProvider<JavaMailSender> mailSender;
  private final String from;

  public OrderConfirmationMailService(
    ObjectProvider<JavaMailSender> mailSender,
    @Value("${lilimi.mail.from:contact@lilimistudio.com}")
    String from
  ) {
    this.mailSender = mailSender;
    this.from = from;
  }

  public void send(OrderConfirmationMessage confirmation) {
    var sender = mailSender.getIfAvailable();

    if (sender == null) {
      throw new IllegalStateException(
        "ORDER_CONFIRMATION_SMTP_UNAVAILABLE"
      );
    }

    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(confirmation.recipient());
    message.setSubject(confirmation.subject());
    message.setText(confirmation.text());

    sender.send(message);
  }
}
