package pl.lilimi.order.persistence;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class OrderReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public OrderReadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void markRead(UUID administratorId, UUID orderId) {
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

  public Set<UUID> findReadIds(
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
}
