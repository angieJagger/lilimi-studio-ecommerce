package pl.lilimi.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
  @NotBlank
  @Pattern(regexp = "pl|en")
  String language,

  @NotNull
  @Valid
  OrderContactRequest contact,

  @NotNull
  @Valid
  OrderDeliveryRequest delivery,

  @NotEmpty
  @Size(max = 100)
  List<@NotNull @Valid OrderItemRequest> items
) {
}
