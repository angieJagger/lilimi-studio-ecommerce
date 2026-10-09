package pl.lilimi.inquiry.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeInquiryStatusRequest(
  @NotBlank
  @Pattern(regexp = "new|in_progress|answered|closed")
  String status,

  @NotNull
  @PositiveOrZero
  Long expectedVersion
) {
}
