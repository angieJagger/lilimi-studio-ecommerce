package pl.lilimi.catalog.digital;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.PostgresTestConfiguration;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class AdminDigitalProductPackageServiceTest {

  @Autowired
  private AdminDigitalProductPackageService service;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoSpyBean
  private DigitalProductPackageRepository packages;

  @MockitoBean
  private DigitalProductPackageStorage storage;

  private final List<String> productIds = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    when(storage.store(any(byte[].class)))
      .thenAnswer(invocation -> UUID.randomUUID() + ".zip");
  }

  @AfterEach
  void cleanUp() {
    for (String productId : productIds) {
      jdbc.update(
        "DELETE FROM digital_product_packages WHERE product_id = ?",
        productId
      );
      jdbc.update(
        "DELETE FROM products WHERE id = ?",
        productId
      );
    }

    productIds.clear();
  }

  @Test
  void shouldAssignDifferentVersionsToConcurrentUploads()
    throws Exception {

    String productId = createProduct("digital");

    var firstUpload = sampleUpload();
    var secondUpload = sampleUpload();

    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> {
        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Upload start timed out");
        }

        return service.upload(productId, firstUpload);
      });

      var second = executor.submit(() -> {
        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Upload start timed out");
        }

        return service.upload(productId, secondUpload);
      });

      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }

      var firstResult = first.get(30, TimeUnit.SECONDS);
      var secondResult = second.get(30, TimeUnit.SECONDS);

      assertThat(List.of(
        firstResult.getPackageVersion(),
        secondResult.getPackageVersion()
      )).containsExactlyInAnyOrder(1, 2);

      assertThat(firstResult.getId())
        .isNotEqualTo(secondResult.getId());

      assertThat(firstResult.getStorageKey())
        .isNotEqualTo(secondResult.getStorageKey());
    }

    var history = packages
      .findByProductIdOrderByPackageVersionDesc(productId);

    assertThat(history)
      .extracting(DigitalProductPackage::getPackageVersion)
      .containsExactly(2, 1);

    assertThat(history)
      .filteredOn(DigitalProductPackage::isCurrentPackage)
      .hasSize(1);

    assertThat(
      packages.findByProductIdAndCurrentPackageTrue(productId)
        .orElseThrow()
        .getPackageVersion()
    ).isEqualTo(2);

    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldCreateFirstCurrentPackage() throws Exception {
    String productId = createProduct("digital");
    var upload = sampleUpload();

    var result = service.upload(productId, upload);

    var saved = packages.findById(result.getId()).orElseThrow();

    assertThat(saved.getProductId()).isEqualTo(productId);
    assertThat(saved.getPackageVersion()).isEqualTo(1);
    assertThat(saved.isCurrentPackage()).isTrue();
    assertThat(saved.getOriginalFilename()).isEqualTo("dragon.zip");
    assertThat(saved.getByteSize()).isEqualTo(upload.getSize());
    assertThat(saved.getSha256()).matches("[0-9a-f]{64}");

    verify(storage).store(upload.getBytes());
    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldKeepOldPackageWhenUploadingNextVersion() throws Exception {
    String productId = createProduct("digital");

    var first = service.upload(productId, sampleUpload());
    var second = service.upload(productId, sampleUpload());

    assertThat(second.getPackageVersion()).isEqualTo(2);

    assertThat(
      packages.findByProductIdAndCurrentPackageTrue(productId)
        .orElseThrow()
        .getId()
    ).isEqualTo(second.getId());

    assertThat(
      packages.findById(first.getId()).orElseThrow().isCurrentPackage()
    ).isFalse();

    assertThat(
      packages.findByProductIdOrderByPackageVersionDesc(productId)
    ).hasSize(2);

    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldRestorePreviousVersionAndDeleteNewFileAfterRollback()
    throws Exception {

    String productId = createProduct("digital");
    var first = service.upload(productId, sampleUpload());

    String failedStorageKey = UUID.randomUUID() + ".zip";
    when(storage.store(any(byte[].class)))
      .thenReturn(failedStorageKey);

    doThrow(new DataIntegrityViolationException("Test database failure"))
      .when(packages)
      .saveAndFlush(any(DigitalProductPackage.class));

    assertThatThrownBy(() ->
      service.upload(productId, sampleUpload())
    ).isInstanceOf(DataIntegrityViolationException.class);

    verify(storage).delete(failedStorageKey);

    var current = packages
      .findByProductIdAndCurrentPackageTrue(productId)
      .orElseThrow();

    assertThat(current.getId()).isEqualTo(first.getId());
    assertThat(current.isCurrentPackage()).isTrue();

    assertThat(
      packages.findByProductIdOrderByPackageVersionDesc(productId)
    ).hasSize(1);
  }

  @Test
  void shouldRejectPackageForPhysicalProduct() throws Exception {
    String productId = createProduct("tshirt");
    var upload = sampleUpload();

    assertThatThrownBy(() -> service.upload(productId, upload))
      .isInstanceOf(ResponseStatusException.class);

    verifyNoInteractions(storage);

    assertThat(
      packages.findByProductIdOrderByPackageVersionDesc(productId)
    ).isEmpty();
  }

  @Test
  void shouldRejectInvalidArchiveBeforeStoringFile() {
    String productId = createProduct("digital");

    var invalid = new MockMultipartFile(
      "file",
      "invalid.zip",
      "application/zip",
      new byte[] {1, 2, 3}
    );

    assertThatThrownBy(() -> service.upload(productId, invalid))
      .isInstanceOf(InvalidDigitalProductPackageException.class);

    verifyNoInteractions(storage);
  }

  @Test
  void shouldReturnEmptyHistoryForDigitalProductWithoutPackages() {
    String productId = createProduct("digital");

    var history = service.findPackages(productId);

    assertThat(history).isEmpty();
    verifyNoInteractions(storage);
  }

  @Test
  void shouldReadHistoryWithoutChangingPackagesOrAccessingFiles()
    throws Exception {

    String productId = createProduct("digital");

    service.upload(productId, sampleUpload());
    service.upload(productId, sampleUpload());

    clearInvocations(storage);

    var history = service.findPackages(productId);

    assertThat(history)
      .extracting(DigitalProductPackage::getPackageVersion)
      .containsExactly(2, 1);

    assertThat(history.get(0).isCurrentPackage()).isTrue();
    assertThat(history.get(1).isCurrentPackage()).isFalse();

    verifyNoInteractions(storage);

    assertThat(
      packages.findByProductIdAndCurrentPackageTrue(productId)
        .orElseThrow()
        .getPackageVersion()
    ).isEqualTo(2);
  }

  @Test
  void shouldRejectHistoryForMissingProduct() {
    String productId = "missing-" + UUID.randomUUID();

    assertThatThrownBy(() -> service.findPackages(productId))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception ->
          assertThat(exception.getStatusCode().value()).isEqualTo(404)
      );

    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectHistoryForPhysicalProduct() {
    String productId = createProduct("tshirt");

    assertThatThrownBy(() -> service.findPackages(productId))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception ->
          assertThat(exception.getStatusCode().value()).isEqualTo(409)
      );

    verifyNoInteractions(storage);
  }

  private String createProduct(String type) {
    String id = "package-test-" + UUID.randomUUID();

    jdbc.update("""
      INSERT INTO products (id, slug, product_type, active)
      VALUES (?, ?, ?, FALSE)
      """, id, id, type);

    productIds.add(id);
    return id;
  }

  private MockMultipartFile sampleUpload() throws Exception {
    var output = new ByteArrayOutputStream();

    try (var zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("dragon.dst"));
      zip.write(new byte[] {1, 2, 3});
      zip.closeEntry();
    }

    return new MockMultipartFile(
      "file",
      "dragon.zip",
      "application/zip",
      output.toByteArray()
    );
  }
}
