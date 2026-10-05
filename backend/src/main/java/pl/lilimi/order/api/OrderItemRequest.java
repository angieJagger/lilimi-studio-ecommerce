package pl.lilimi.order.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "kind"
)
@JsonSubTypes({
  @JsonSubTypes.Type(
    value = OrderItemRequest.Digital.class,
    name = "digital"
  ),
  @JsonSubTypes.Type(
    value = OrderItemRequest.Sweatshirt.class,
    name = "sweatshirt"
  )
})
public sealed interface OrderItemRequest
  permits OrderItemRequest.Digital, OrderItemRequest.Sweatshirt {

  record Digital(
    @NotBlank
    @Size(max = 100)
    String productId,

    @NotNull
    @Min(1)
    @Max(1)
    Integer quantity
  ) implements OrderItemRequest {
  }

  record Sweatshirt(
    @NotBlank
    @Size(max = 100)
    String productId,

    @NotBlank
    @Size(max = 100)
    String patternId,

    @NotNull
    @Valid
    SweatshirtConfigurationRequest configuration,

    @NotNull
    @Min(1)
    @Max(99)
    Integer quantity
  ) implements OrderItemRequest {
  }
}
