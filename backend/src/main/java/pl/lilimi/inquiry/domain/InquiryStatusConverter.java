package pl.lilimi.inquiry.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class InquiryStatusConverter
  implements AttributeConverter<InquiryStatus, String> {

  @Override
  public String convertToDatabaseColumn(InquiryStatus status) {
    return status == null ? null : status.getValue();
  }

  @Override
  public InquiryStatus convertToEntityAttribute(String value) {
    return value == null ? null : InquiryStatus.fromValue(value);
  }
}
