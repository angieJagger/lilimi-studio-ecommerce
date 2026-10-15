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
public class PasswordResetMailService {

  private final ObjectProvider<JavaMailSender> mailSender;
  private final String from;
  private final String publicBaseUrl;

  public PasswordResetMailService(
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
      + "/account/reset-password#token=" + rawToken;

    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject(
      polish
        ? "Lilimi Studio — ustaw nowe hasło"
        : "Lilimi Studio — reset your password"
    );

    message.setText(
      polish
        ? """
          Otrzymaliśmy prośbę o zmianę hasła do Twojego konta Lilimi Studio.

          Aby ustawić nowe hasło, otwórz poniższy link:

          %s

          Link jest ważny przez 30 minut i można go użyć tylko raz.
          Po zmianie hasła wcześniejsze sesje zostaną unieważnione.

          Jeśli nie prosisz o zmianę hasła, zignoruj tę wiadomość.
          Samo otrzymanie wiadomości nie zmienia Twojego hasła.

          Lilimi Studio
          """.formatted(link)
        : """
          We received a request to reset your Lilimi Studio password.

          To set a new password, open this link:

          %s

          The link expires in 30 minutes and can only be used once.
          Changing your password will invalidate existing sessions.

          If you did not request a password reset, ignore this message.
          Receiving this email does not change your password.

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
