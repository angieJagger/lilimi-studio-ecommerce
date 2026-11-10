package pl.lilimi.catalog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class PhysicalDraftConfigurationConverter
  implements AttributeConverter<PhysicalDraftConfiguration, String> {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Override
  public String convertToDatabaseColumn(PhysicalDraftConfiguration configuration) {
    return configuration == null ? null : MAPPER.writeValueAsString(configuration);
  }

  @Override
  public PhysicalDraftConfiguration convertToEntityAttribute(String storedConfiguration) {
    return storedConfiguration == null
      ? null
      : MAPPER.readValue(storedConfiguration, PhysicalDraftConfiguration.class);
  }
}
