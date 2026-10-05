package pl.lilimi.order.domain;

public enum OrderStatus {
  NEW("new"),
  PROCESSING("processing"),
  COMPLETED("completed"),
  CANCELLED("cancelled");

  private final String value;

  OrderStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static OrderStatus fromValue(String value) {
    for (var status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }

    throw new IllegalArgumentException(
      "Unknown order status: " + value
    );
  }
}
