package pl.lilimi.catalog;

import java.util.List;
import java.util.Map;

public record ProductResponse(
  String id,
  String slug,
  String productType,
  Map<String, String> name,
  Map<String, String> description,
  int priceInGrosz,
  String priceType,
  String currency,
  List<String> fileFormats,
  boolean madeToOrder,
  boolean personalizationAvailable
) {
  public ProductResponse {
    name = Map.copyOf(name);
    description = Map.copyOf(description);
    fileFormats = List.copyOf(fileFormats);
  }
}
