package pl.lilimi.order.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "kind"
)
@JsonSubTypes({
  @JsonSubTypes.Type(
    value = OrderDeliveryRequest.Digital.class,
    name = "digital"
  ),
  @JsonSubTypes.Type(
    value = OrderDeliveryRequest.Courier.class,
    name = "courier"
  )
})
public sealed interface OrderDeliveryRequest
  permits OrderDeliveryRequest.Digital, OrderDeliveryRequest.Courier {

  record Digital() implements OrderDeliveryRequest {
  }

  record Courier(
    @NotBlank
    @Pattern(regexp = "dhl-courier|dpd-courier|inpost-courier")
    String methodId,

    @NotNull
    @Valid
    OrderAddressRequest address
  ) implements OrderDeliveryRequest {
  }
}
