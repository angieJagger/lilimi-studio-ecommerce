package pl.lilimi.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public record PhysicalDraftConfiguration(
  @Size(max = 80) String category,
  @Positive Integer priceInGrosz,
  @NotNull @Size(max = 6) List<@NotNull @Valid VariantAttribute> variantAttributes,
  @Valid ShippingPackage shippingPackage
) {

  public record ShippingPackage(
    @Positive @Digits(integer = 4, fraction = 3) BigDecimal lengthCm,
    @Positive @Digits(integer = 4, fraction = 3) BigDecimal widthCm,
    @Positive @Digits(integer = 4, fraction = 3) BigDecimal heightCm,
    @Positive @Digits(integer = 4, fraction = 3) BigDecimal weightKg
  ) {}

  public PhysicalDraftConfiguration withPrice(int priceInGrosz) {
    return new PhysicalDraftConfiguration(category, priceInGrosz, variantAttributes, shippingPackage);
  }

  public record VariantAttribute(
    @NotBlank @Size(max = 60) String name,
    @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 60) String> values
  ) {
    @JsonIgnore
    @AssertTrue(message = "Variant values must be unique")
    public boolean isValuesUnique() {
      return values == null || values.stream()
        .filter(value -> value != null)
        .map(value -> value.strip().toLowerCase(Locale.ROOT))
        .distinct().count() == values.size();
    }
  }

  @JsonIgnore
  @AssertTrue(message = "Set a single price or define distinct variant attributes")
  public boolean isConfigurationValid() {
    if (variantAttributes == null) {
      return true;
    }

    if (variantAttributes.isEmpty()) {
      return priceInGrosz != null;
    }

    return variantAttributes.stream()
      .filter(attribute -> attribute != null && attribute.name() != null)
      .map(attribute -> attribute.name().strip().toLowerCase(Locale.ROOT))
      .distinct().count() == variantAttributes.size();
  }
}
