package pl.lilimi.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalDownloadMailServiceTest {

  @Mock
  private ObjectProvider<JavaMailSender> mailSender;

  @Mock
  private JavaMailSender sender;

  private DigitalDownloadMailService service;

  @BeforeEach
  void setUp() {
    service = new DigitalDownloadMailService(
      mailSender,
      "contact@lilimistudio.com"
    );
  }

  @Test
  void shouldSendPreparedMessage() {
    when(mailSender.getIfAvailable()).thenReturn(sender);

    var message = createMessage();

    service.send(message);

    var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(captor.capture());

    var sent = captor.getValue();

    assertThat(sent.getFrom())
      .isEqualTo("contact@lilimistudio.com");
    assertThat(sent.getTo())
      .containsExactly(message.recipient());
    assertThat(sent.getSubject()).isEqualTo(message.subject());
    assertThat(sent.getText()).isEqualTo(message.text());
  }

  @Test
  void shouldReportMissingSmtpConfiguration() {
    when(mailSender.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> service.send(createMessage()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("DIGITAL_DOWNLOAD_SMTP_UNAVAILABLE");
  }

  @Test
  void shouldPropagateSmtpFailure() {
    when(mailSender.getIfAvailable()).thenReturn(sender);

    var failure = new MailSendException("Simulated SMTP failure");

    doThrow(failure)
      .when(sender)
      .send(any(SimpleMailMessage.class));

    assertThatThrownBy(() -> service.send(createMessage()))
      .isSameAs(failure);
  }

  private DigitalDownloadEmailMessage createMessage() {
    return new DigitalDownloadEmailMessage(
      "customer@example.com",
      "Lilimi Studio — pliki do pobrania",
      "Przygotowana treść wiadomości z linkiem."
    );
  }
}
