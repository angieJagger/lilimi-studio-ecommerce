package pl.lilimi.inquiry.persistence;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class InquiryReadRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public InquiryReadRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void markRead(UUID administratorId, UUID inquiryId) {
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

  public Set<UUID> findReadIds(
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
