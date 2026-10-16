package pl.lilimi.order.application;

import org.springframework.stereotype.Component;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderItem;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class OrderConfirmationMessageFactory {

  public OrderConfirmationMessage create(
    Order order,
    List<OrderItem> items
  ) {
    Objects.requireNonNull(order, "Order is required");
    Objects.requireNonNull(items, "Order items are required");
    Objects.requireNonNull(order.getId(), "Saved order is required");

    if (items.isEmpty()) {
      throw new IllegalArgumentException(
        "Order items must not be empty"
      );
    }

    boolean polish = "pl".equals(order.getLanguage());
    var text = new StringBuilder();

    text.append(polish
      ? "Dziękujemy za złożenie zamówienia w Lilimi Studio.\n\n"
      : "Thank you for placing an order with Lilimi Studio.\n\n"
    );

    line(
      text,
      polish ? "Numer zamówienia" : "Order reference",
      order.getId().toString()
    );

    text.append(polish
      ? "\nOtrzymaliśmy Twoje zamówienie. "
      + "Ta wiadomość nie jest potwierdzeniem płatności.\n"
      : "\nWe have received your order. "
      + "This email is not a payment confirmation.\n"
    );

    text.append(polish
      ? "\nPRODUKTY\n"
      : "\nITEMS\n"
    );

    for (var item : items) {
      text.append("\n")
        .append(item.getPosition())
        .append(". ")
        .append(singleLine(item.getProductName()))
        .append("\n");

      if ("sweatshirt".equals(item.getKind())) {
        line(
          text,
          polish ? "Wzór haftu" : "Embroidery pattern",
          item.getPatternName()
        );

        line(
          text,
          polish ? "Krój" : "Fit",
          translate(item.getFit(), polish)
        );

        line(
          text,
          polish ? "Rozmiar" : "Size",
          item.getSize()
        );

        line(
          text,
          polish ? "Kolor" : "Colour",
          translate(item.getColor(), polish)
        );

        line(
          text,
          polish ? "Haft" : "Embroidery",
          translate(item.getEmbroideryOptionId(), polish)
        );
      }

      line(
        text,
        polish ? "Liczba sztuk" : "Quantity",
        Integer.toString(item.getQuantity())
      );

      line(
        text,
        polish ? "Cena za sztukę" : "Unit price",
        money(item.getUnitPriceInGrosz(), order, polish)
      );

      line(
        text,
        polish ? "Wartość pozycji" : "Item total",
        money(item.getLineTotalInGrosz(), order, polish)
      );
    }

    text.append(polish
      ? "\nPODSUMOWANIE\n"
      : "\nSUMMARY\n"
    );

    line(
      text,
      polish ? "Produkty" : "Items",
      money(order.getSubtotalInGrosz(), order, polish)
    );

    line(
      text,
      polish ? "Dostawa" : "Delivery",
      money(order.getDeliveryPriceInGrosz(), order, polish)
    );

    line(
      text,
      polish ? "Razem" : "Total",
      money(order.getTotalInGrosz(), order, polish)
    );

    text.append(polish
      ? "\nDANE KONTAKTOWE\n"
      : "\nCONTACT DETAILS\n"
    );

    var contact = order.getContact();

    line(
      text,
      polish ? "Imię i nazwisko" : "Full name",
      contact.getFullName()
    );

    line(text, "Email", contact.getEmail());

    line(
      text,
      polish ? "Telefon" : "Phone",
      contact.getPhone()
    );

    var delivery = order.getDelivery();

    if ("courier".equals(delivery.getKind())) {
      text.append(polish
        ? "\nDOSTAWA KURIEREM\n"
        : "\nCOURIER DELIVERY\n"
      );

      line(
        text,
        polish ? "Przewoźnik" : "Courier",
        translate(delivery.getMethodId(), polish)
      );

      line(
        text,
        polish ? "Adres" : "Address",
        delivery.getAddressLine1()
      );

      line(
        text,
        polish ? "Dodatkowe dane adresowe" : "Address details",
        delivery.getAddressLine2()
      );

      line(
        text,
        polish ? "Kod pocztowy" : "Postal code",
        delivery.getPostalCode()
      );

      line(
        text,
        polish ? "Miejscowość" : "City",
        delivery.getCity()
      );

      line(
        text,
        polish ? "Kraj" : "Country",
        "PL".equals(delivery.getCountryCode())
          ? (polish ? "Polska" : "Poland")
          : delivery.getCountryCode()
      );
    } else {
      text.append(polish
        ? "\nZamówienie zawiera wyłącznie produkty cyfrowe "
        + "i nie wymaga dostawy kurierskiej.\n"
        : "\nThis order contains only digital products "
        + "and does not require courier delivery.\n"
      );
    }

    text.append(polish
      ? "\nLilimi Studio działa obecnie w wersji demonstracyjnej. "
      + "Nie pobrano płatności. Realizacja zamówień i automatyczne "
      + "udostępnianie zakupionych plików nie są jeszcze dostępne.\n"
      : "\nLilimi Studio is currently a demonstration application. "
      + "No payment has been collected. Order fulfilment and "
      + "automatic delivery of purchased files are not yet available.\n"
    );

    text.append("\nLilimi Studio\n");

    return new OrderConfirmationMessage(
      contact.getEmail(),
      polish
        ? "Lilimi Studio — przyjęliśmy zamówienie " + order.getId()
        : "Lilimi Studio — order received " + order.getId(),
      text.toString()
    );
  }

  private static void line(
    StringBuilder text,
    String label,
    String value
  ) {
    if (value == null || value.isBlank()) {
      return;
    }

    text.append(label)
      .append(": ")
      .append(singleLine(value))
      .append("\n");
  }

  private static String singleLine(String value) {
    return value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ")
      .strip();
  }

  private static String money(
    long amountInGrosz,
    Order order,
    boolean polish
  ) {
    var locale = polish
      ? Locale.forLanguageTag("pl-PL")
      : Locale.UK;

    var format = NumberFormat.getNumberInstance(locale);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);

    return format.format(BigDecimal.valueOf(amountInGrosz, 2))
      + " " + order.getCurrency();
  }

  private static String translate(String value, boolean polish) {
    if (value == null) {
      return null;
    }

    return switch (value) {
      case "women" -> polish ? "Damski" : "Women's";
      case "men" -> polish ? "Męski" : "Men's";
      case "children" -> polish ? "Dziecięcy" : "Children's";
      case "black" -> polish ? "Czarny" : "Black";
      case "white" -> polish ? "Biały" : "White";
      case "navy" -> polish ? "Granatowy" : "Navy";
      case "grey", "gray" -> polish ? "Szary" : "Grey";
      case "beige" -> polish ? "Beżowy" : "Beige";
      case "small-front" ->
        polish ? "Mały haft z przodu" : "Small front embroidery";
      case "large-back" ->
        polish ? "Duży haft z tyłu" : "Large back embroidery";
      case "dhl-courier" -> "DHL";
      case "dpd-courier" -> "DPD";
      case "inpost-courier" -> "InPost";
      default -> value;
    };
  }
}
