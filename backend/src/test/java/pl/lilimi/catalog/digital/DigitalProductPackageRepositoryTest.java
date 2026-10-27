package pl.lilimi.catalog.digital;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class DigitalProductPackageRepositoryTest {

  @Autowired
  private DigitalProductPackageRepository packages;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSaveAndReadPackageMetadata() {
    String productId = createProduct();

    assertThat(packages.findLatestVersion(productId)).isZero();
    assertThat(
      packages.findByProductIdAndCurrentPackageTrue(productId)
    ).isEmpty();

    var file = newPackage(productId, 1);
    file.makeCurrent();
    packages.saveAndFlush(file);

    entityManager.clear();

    var saved = packages
      .findByProductIdAndCurrentPackageTrue(productId)
      .orElseThrow();

    assertThat(saved.getId()).isEqualTo(file.getId());
    assertThat(saved.getProductId()).isEqualTo(productId);
    assertThat(saved.getPackageVersion()).isEqualTo(1);
    assertThat(saved.getStorageKey()).isEqualTo(file.getStorageKey());
    assertThat(saved.getOriginalFilename()).isEqualTo("dragon.zip");
    assertThat(saved.getByteSize()).isEqualTo(1024);
    assertThat(saved.getSha256()).isEqualTo("a".repeat(64));
    assertThat(saved.getCreatedAt())
      .isEqualTo(Instant.parse("2026-10-27T10:00:00Z"));
  }

  @Test
  void shouldKeepPreviousVersionWhenReplacingCurrentPackage() {
    String productId = createProduct();

    var first = newPackage(productId, 1);
    first.makeCurrent();
    packages.saveAndFlush(first);

    first.retire();
    packages.saveAndFlush(first);

    var second = newPackage(productId, 2);
    second.makeCurrent();
    packages.saveAndFlush(second);

    entityManager.clear();

    assertThat(packages.findLatestVersion(productId)).isEqualTo(2);

    assertThat(
      packages.findByProductIdOrderByPackageVersionDesc(productId)
    )
      .extracting(DigitalProductPackage::getPackageVersion)
      .containsExactly(2, 1);

    assertThat(
      packages.findByProductIdAndCurrentPackageTrue(productId)
        .orElseThrow()
        .getId()
    ).isEqualTo(second.getId());

    assertThat(
      packages.findById(first.getId()).orElseThrow().isCurrentPackage()
    ).isFalse();
  }

  @Test
  void shouldRejectTwoCurrentPackagesForOneProduct() {
    String productId = createProduct();

    var first = newPackage(productId, 1);
    first.makeCurrent();
    packages.saveAndFlush(first);

    var second = newPackage(productId, 2);
    second.makeCurrent();

    assertThatThrownBy(() -> packages.saveAndFlush(second))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String createProduct() {
    String id = "digital-test-" + UUID.randomUUID();

    jdbc.update("""
      INSERT INTO products (id, slug, product_type, active)
      VALUES (?, ?, 'digital', FALSE)
      """, id, id);

    return id;
  }

  private DigitalProductPackage newPackage(
    String productId,
    int version
  ) {
    return new DigitalProductPackage(
      productId,
      version,
      UUID.randomUUID() + ".zip",
      "dragon.zip",
      1024,
      "a".repeat(64),
      Instant.parse("2026-10-27T10:00:00Z")
    );
  }
}
