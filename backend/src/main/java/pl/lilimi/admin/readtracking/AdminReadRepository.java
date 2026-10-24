package pl.lilimi.admin.readtracking;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AdminReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public AdminReadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void markOrderRead(UUID administratorId, UUID orderId) {
    jdbc.update(
      """
      INSERT INTO admin_order_reads (
        administrator_id,
        order_id
      )
      VALUES (:administratorId, :orderId)
      ON CONFLICT (administrator_id, order_id) DO NOTHING
      """,
      Map.of(
        "administratorId", administratorId,
        "orderId", orderId
      )
    );
  }

  @Transactional
  public void markInquiryRead(UUID administratorId, UUID inquiryId) {
    jdbc.update(
      """
      INSERT INTO admin_inquiry_reads (
        administrator_id,
        inquiry_id
      )
      VALUES (:administratorId, :inquiryId)
      ON CONFLICT (administrator_id, inquiry_id) DO NOTHING
      """,
      Map.of(
        "administratorId", administratorId,
        "inquiryId", inquiryId
      )
    );
  }

  public Set<UUID> findReadOrderIds(
    UUID administratorId,
    Collection<UUID> orderIds
  ) {
    if (orderIds.isEmpty()) {
      return Set.of();
    }

    return Set.copyOf(
      jdbc.query(
        """
        SELECT order_id
        FROM admin_order_reads
        WHERE administrator_id = :administratorId
          AND order_id IN (:orderIds)
        """,
        Map.of(
          "administratorId", administratorId,
          "orderIds", orderIds
        ),
        (resultSet, rowNumber) ->
          resultSet.getObject("order_id", UUID.class)
      )
    );
  }

  public Set<UUID> findReadInquiryIds(
    UUID administratorId,
    Collection<UUID> inquiryIds
  ) {
    if (inquiryIds.isEmpty()) {
      return Set.of();
    }

    return Set.copyOf(
      jdbc.query(
        """
        SELECT inquiry_id
        FROM admin_inquiry_reads
        WHERE administrator_id = :administratorId
          AND inquiry_id IN (:inquiryIds)
        """,
        Map.of(
          "administratorId", administratorId,
          "inquiryIds", inquiryIds
        ),
        (resultSet, rowNumber) ->
          resultSet.getObject("inquiry_id", UUID.class)
      )
    );
  }
}
