package pl.lilimi.payment.status;

import java.util.UUID;

public record PaymentStatusResult(
  UUID paymentAttemptId,
  UUID orderId,
  String status
) {
}
