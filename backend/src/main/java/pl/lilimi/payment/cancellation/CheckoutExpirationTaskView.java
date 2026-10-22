package pl.lilimi.payment.cancellation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

@Entity
@Immutable
@Table(name = "checkout_expiration_tasks")
public class CheckoutExpirationTaskView {

  @Id
  @Column(name = "payment_attempt_id")
  private UUID paymentAttemptId;

  @Column(name = "status")
  private String status;

  protected CheckoutExpirationTaskView() {
    // Required by JPA.
  }
}
