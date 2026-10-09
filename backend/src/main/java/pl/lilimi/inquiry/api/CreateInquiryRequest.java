package pl.lilimi.inquiry.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.net.URISyntaxException;

public record CreateInquiryRequest(
  @NotBlank
  @Pattern(regexp = "pl|en")
  String language,

  @NotBlank
  @Size(max = 150)
  String name,

  @NotBlank
  @Email
  @Size(max = 254)
  String email,

  @NotBlank
  @Pattern(regexp = "embroideredProduct|digitizing|website|other")
  String projectType,

  @NotBlank
  @Size(max = 5000)
  String description,

  @Size(max = 2048)
  String inspirationUrl,

  @Size(max = 100)
  String productId
) {

  @AssertTrue(message = "Inspiration URL must be a valid HTTP or HTTPS URL")
  public boolean isInspirationUrlValid() {
    if (inspirationUrl == null || inspirationUrl.isBlank()) {
      return true;
    }

    try {
      var uri = new URI(inspirationUrl.trim());
      var scheme = uri.getScheme();

      return (
        "http".equalsIgnoreCase(scheme) ||
          "https".equalsIgnoreCase(scheme)
      ) &&
        uri.getHost() != null &&
        !uri.getHost().isBlank() &&
        uri.getRawUserInfo() == null;
    } catch (URISyntaxException exception) {
      return false;
    }
  }
}
