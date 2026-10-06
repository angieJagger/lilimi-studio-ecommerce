package pl.lilimi.order.persistence;

import java.util.UUID;

public record OrderSubmission(
  UUID idempotencyKey,
  String requestHash,
  UUID orderId
) {
}
