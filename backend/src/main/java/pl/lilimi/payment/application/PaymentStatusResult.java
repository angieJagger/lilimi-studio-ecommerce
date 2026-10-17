package pl.lilimi.payment.application;

import java.util.UUID;

public record PaymentStatusResult(
  UUID paymentAttemptId,
  String status
) {
}
