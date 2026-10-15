package pl.lilimi.auth.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationMailService {

  private final ObjectProvider<JavaMailSender> mailSender;
  private final String from;
  private final String publicBaseUrl;

  public EmailVerificationMailService(
    ObjectProvider<JavaMailSender> mailSender,
    @Value("${lilimi.mail.from:contact@lilimistudio.com}") String from,
    @Value("${lilimi.public-base-url:https://lilimistudio.com}")
    String publicBaseUrl
  ) {
    this.mailSender = mailSender;
    this.from = from;
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  public void send(String email, String rawToken, String language) {
    var sender = mailSender.getIfAvailable();

    if (sender == null) {
      throw mailUnavailable();
    }

    boolean polish = "pl".equals(language);
    String locale = polish ? "pl" : "en";

    String link = publicBaseUrl
      + "/" + locale
      + "/account/verify-email#token=" + rawToken;

    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject(
      polish
        ? "Lilimi Studio — potwierdź adres e-mail"
        : "Lilimi Studio — verify your email address"
    );

    message.setText(
      polish
        ? """
          Dziękujemy za założenie konta w Lilimi Studio.

          Potwierdź adres e-mail, otwierając poniższy link:

          %s

          Link jest ważny przez 24 godziny i można go użyć tylko raz.
          Jeśli nie zakładałaś ani nie zakładałeś konta, zignoruj tę wiadomość.

          Lilimi Studio
          """.formatted(link)
        : """
          Thank you for creating a Lilimi Studio account.

          Verify your email address by opening this link:

          %s

          The link expires in 24 hours and can only be used once.
          If you did not create an account, please ignore this message.

          Lilimi Studio
          """.formatted(link)
    );

    try {
      sender.send(message);
    } catch (MailException exception) {
      throw mailUnavailable();
    }
  }

  private ResponseStatusException mailUnavailable() {
    return new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "EMAIL_DELIVERY_UNAVAILABLE"
    );
  }
}
