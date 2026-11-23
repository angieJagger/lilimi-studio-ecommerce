package pl.lilimi.notification.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AdminNotificationQueue {

  private final JdbcTemplate jdbc;
  private final AdminNotificationProperties properties;

  public AdminNotificationQueue(
    JdbcTemplate jdbc,
    AdminNotificationProperties properties
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
  }

  public int enqueueInquiry(UUID inquiryId) {
    return enqueue("INQUIRY_CREATED", inquiryId);
  }

  public int enqueuePaidOrder(UUID orderId) {
    return enqueue("ORDER_PAID", orderId);
  }

  private int enqueue(String eventType, UUID resourceId) {
    Objects.requireNonNull(resourceId, "Resource ID is required");

    int inserted = 0;

    if (properties.telegram().enabled()) {
      inserted += insert(eventType, resourceId, "TELEGRAM");
    }

    if (properties.email().enabled()) {
      inserted += insert(eventType, resourceId, "EMAIL");
    }

    return inserted;
  }

  private int insert(
    String eventType,
    UUID resourceId,
    String channel
  ) {
    return jdbc.update("""
      INSERT INTO admin_notifications (
        id,
        event_type,
        resource_id,
        channel
      )
      VALUES (?, ?, ?, ?)
      ON CONFLICT ON CONSTRAINT uq_admin_notifications_event_channel
      DO NOTHING
      """,
      UUID.randomUUID(),
      eventType,
      resourceId,
      channel
    );
  }
}
