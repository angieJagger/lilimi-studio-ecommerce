package pl.lilimi.inquiry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.InquiryReadRepository;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class InquiryReadRepositoryTest {

  @Autowired
  private InquiryReadRepository reads;

  @Autowired
  private ProjectInquiryRepository inquiries;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldKeepFirstReadTimestampWithoutCreatingDuplicates() {
    UUID administratorId = saveAdministrator();
    UUID inquiryId = saveInquiry();

    assertThat(reads.findReadIds(administratorId, List.of(inquiryId)))
      .isEmpty();

    reads.markRead(administratorId, inquiryId);

    Instant firstRead = Instant.parse("2026-01-01T12:00:00Z");

    jdbc.update(
      """
      UPDATE admin_inquiry_reads
      SET read_at = ?
      WHERE administrator_id = ? AND inquiry_id = ?
      """,
      Timestamp.from(firstRead),
      administratorId,
      inquiryId
    );

    reads.markRead(administratorId, inquiryId);

    assertThat(reads.findReadIds(administratorId, List.of(inquiryId)))
      .containsExactly(inquiryId);

    assertThat(jdbc.queryForObject(
      """
      SELECT COUNT(*) FROM admin_inquiry_reads
      WHERE administrator_id = ? AND inquiry_id = ?
      """,
      Long.class,
      administratorId,
      inquiryId
    )).isEqualTo(1L);

    var savedTimestamp = jdbc.queryForObject(
      """
      SELECT read_at FROM admin_inquiry_reads
      WHERE administrator_id = ? AND inquiry_id = ?
      """,
      Timestamp.class,
      administratorId,
      inquiryId
    );

    assertThat(savedTimestamp).isNotNull();
    assertThat(savedTimestamp.toInstant()).isEqualTo(firstRead);
  }

  @Test
  void shouldRestrictReadsToAdministratorAndRequestedInquiries() {
    UUID firstAdmin = saveAdministrator();
    UUID secondAdmin = saveAdministrator();
    UUID firstInquiry = saveInquiry();
    UUID secondInquiry = saveInquiry();
    UUID unreadInquiry = saveInquiry();

    reads.markRead(firstAdmin, firstInquiry);
    reads.markRead(firstAdmin, secondInquiry);
    reads.markRead(secondAdmin, secondInquiry);

    var requestedIds = List.of(
      firstInquiry,
      secondInquiry,
      unreadInquiry
    );

    assertThat(reads.findReadIds(firstAdmin, requestedIds))
      .containsExactlyInAnyOrder(firstInquiry, secondInquiry);

    assertThat(reads.findReadIds(secondAdmin, requestedIds))
      .containsExactly(secondInquiry);

    assertThat(reads.findReadIds(firstAdmin, List.of(secondInquiry)))
      .containsExactly(secondInquiry);

    assertThat(reads.findReadIds(firstAdmin, List.of()))
      .isEmpty();
  }

  private UUID saveAdministrator() {
    return accounts.saveAndFlush(
      new UserAccount(
        UUID.randomUUID() + "@example.com",
        "test-password-hash",
        UserRole.ADMIN
      )
    ).getId();
  }

  private UUID saveInquiry() {
    return inquiries.saveAndFlush(
      new ProjectInquiry(
        "pl",
        "Anna Kowalska",
        "anna@example.com",
        "website",
        "Proszę o wycenę strony.",
        null,
        null
      )
    ).getId();
  }
}
