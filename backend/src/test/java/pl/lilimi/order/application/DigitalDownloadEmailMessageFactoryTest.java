package pl.lilimi.order.application;

import org.junit.jupiter.api.Test;

import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DigitalDownloadEmailMessageFactoryTest {

  private static final String RAW_TOKEN = "a".repeat(43);

  private static final Instant EXPIRES_AT =
    Instant.parse("2026-11-08T10:00:00Z");

  private final DigitalDownloadEmailMessageFactory factory =
    new DigitalDownloadEmailMessageFactory(
      "https://lilimistudio.com/"
    );

  @Test
  void shouldCreatePolishMessageWithDownloadLink() {
    var order = createOrder("pl");

    var message = factory.create(order, tokenFor(order));

    assertThat(message.recipient())
      .isEqualTo("customer@example.com");
    assertThat(message.subject())
      .isEqualTo("Lilimi Studio — pliki do pobrania");
    assertThat(message.text())
      .contains(
        order.getId().toString(),
        "Płatność",
        "https://lilimistudio.com/pl/downloads#token=" + RAW_TOKEN,
        "2026-11-08 10:00 UTC"
      )
      .doesNotContain("/pl/downloads?token=");

    assertThat(message.toString())
      .doesNotContain(RAW_TOKEN, "customer@example.com");
  }

  @Test
  void shouldCreateEnglishMessage() {
    var order = createOrder("en");

    var message = factory.create(order, tokenFor(order));

    assertThat(message.subject())
      .isEqualTo("Lilimi Studio — your downloads");
    assertThat(message.text())
      .contains(
        "Payment for order",
        "https://lilimistudio.com/en/downloads#token=" + RAW_TOKEN,
        "2026-11-08 10:00 UTC"
      );
  }

  @Test
  void shouldRejectTokenBelongingToAnotherOrder() {
    var order = createOrder("pl");

    var token = new IssuedDigitalDownloadToken(
      UUID.randomUUID(),
      RAW_TOKEN,
      EXPIRES_AT
    );

    assertThatIllegalArgumentException()
      .isThrownBy(() -> factory.create(order, token));
  }

  @Test
  void shouldRejectMalformedToken() {
    var order = createOrder("pl");

    var token = new IssuedDigitalDownloadToken(
      order.getId(),
      "invalid",
      EXPIRES_AT
    );

    assertThatIllegalArgumentException()
      .isThrownBy(() -> factory.create(order, token));
  }

  @Test
  void shouldAllowLocalDevelopmentAddress() {
    var localFactory = new DigitalDownloadEmailMessageFactory(
      "http://localhost:4200"
    );

    var order = createOrder("pl");

    assertThat(localFactory.create(order, tokenFor(order)).text())
      .contains(
        "http://localhost:4200/pl/downloads#token=" + RAW_TOKEN
      );
  }

  @Test
  void shouldRejectInvalidPublicAddresses() {
    String[] addresses = {
      "http://lilimistudio.com",
      "https://user@lilimistudio.com",
      "https://lilimistudio.com?redirect=other",
      "https://lilimistudio.com#fragment",
      "https://lilimistudio.com/unexpected",
      "/relative",
    };

    for (String address : addresses) {
      assertThatIllegalArgumentException()
        .as("Address: %s", address)
        .isThrownBy(() ->
          new DigitalDownloadEmailMessageFactory(address)
        );
    }
  }

  private Order createOrder(String language) {
    var order = mock(Order.class);

    when(order.getId()).thenReturn(UUID.randomUUID());
    when(order.getLanguage()).thenReturn(language);
    when(order.getContact()).thenReturn(new OrderContact(
      "Test Customer",
      "customer@example.com",
      null
    ));

    return order;
  }

  private IssuedDigitalDownloadToken tokenFor(Order order) {
    return new IssuedDigitalDownloadToken(
      order.getId(),
      RAW_TOKEN,
      EXPIRES_AT
    );
  }
}
