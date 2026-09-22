package pl.lilimi.catalog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class ProductTypeConverter
  implements AttributeConverter<ProductType, String> {

  @Override
  public String convertToDatabaseColumn(ProductType value) {
    return value == null
      ? null
      : value.name().toLowerCase(Locale.ROOT);
  }

  @Override
  public ProductType convertToEntityAttribute(String value) {
    return value == null
      ? null
      : ProductType.valueOf(value.toUpperCase(Locale.ROOT));
  }
}
