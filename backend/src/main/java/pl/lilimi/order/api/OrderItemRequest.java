package pl.lilimi.order.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

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
  ),
  @JsonSubTypes.Type(value = OrderItemRequest.Physical.class, name = "physical")
})
public sealed interface OrderItemRequest
  permits OrderItemRequest.Digital, OrderItemRequest.Sweatshirt, OrderItemRequest.Physical {

  String productId();

  Integer quantity();

  record Physical(
    @NotBlank @Size(max = 100) String productId,
    @NotNull @Size(max = 6) Map<@NotBlank @Size(max = 60) String, @NotBlank @Size(max = 60) String> options,
    @NotNull @Min(1) Integer quantity
  ) implements OrderItemRequest {
  }

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
    Integer quantity
  ) implements OrderItemRequest {
  }
}
