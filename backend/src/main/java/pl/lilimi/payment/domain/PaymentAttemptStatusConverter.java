package pl.lilimi.payment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PaymentAttemptStatusConverter
  implements AttributeConverter<PaymentAttemptStatus, String> {

  @Override
  public String convertToDatabaseColumn(
    PaymentAttemptStatus status
  ) {
    return status == null ? null : status.getValue();
  }

  @Override
  public PaymentAttemptStatus convertToEntityAttribute(
    String value
  ) {
    return value == null
      ? null
      : PaymentAttemptStatus.fromValue(value);
  }
}
