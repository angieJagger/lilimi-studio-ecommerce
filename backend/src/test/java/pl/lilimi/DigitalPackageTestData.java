package pl.lilimi;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public final class DigitalPackageTestData {

  private DigitalPackageTestData() {
  }

  public static UUID currentPackageId(
    JdbcTemplate jdbc,
    String productId
  ) {
    var existing = jdbc.queryForList(
      """
      SELECT id
      FROM digital_product_packages
      WHERE product_id = ?
        AND current_package = TRUE
      """,
      UUID.class,
      productId
    );

    if (!existing.isEmpty()) {
      return existing.getFirst();
    }

    var packageId = UUID.randomUUID();

    jdbc.update(
      """
      INSERT INTO digital_product_packages (
          id,
          product_id,
          package_version,
          storage_key,
          original_filename,
          byte_size,
          sha256,
          current_package,
          created_at
      )
      SELECT
          ?,
          ?,
          COALESCE(MAX(package_version), 0) + 1,
          ?,
          'test-package.zip',
          128,
          ?,
          TRUE,
          CURRENT_TIMESTAMP
      FROM digital_product_packages
      WHERE product_id = ?
      """,
      packageId,
      productId,
      packageId + ".zip",
      "a".repeat(64),
      productId
    );

    return packageId;
  }

  public static UUID createHistoricalPackage(
    JdbcTemplate jdbc,
    String productId
  ) {
    var packageId = UUID.randomUUID();

    jdbc.update(
      """
      INSERT INTO digital_product_packages (
          id,
          product_id,
          package_version,
          storage_key,
          original_filename,
          byte_size,
          sha256,
          current_package,
          created_at
      )
      SELECT
          ?,
          ?,
          COALESCE(MAX(package_version), 0) + 1,
          ?,
          'test-package.zip',
          128,
          ?,
          FALSE,
          CURRENT_TIMESTAMP
      FROM digital_product_packages
      WHERE product_id = ?
      """,
      packageId,
      productId,
      packageId + ".zip",
      "a".repeat(64),
      productId
    );

    return packageId;
  }
}
