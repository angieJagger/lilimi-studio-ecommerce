package pl.lilimi.catalog.digital;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemDigitalProductPackageStorageTest {

  @TempDir
  Path mediaDirectory;

  private FileSystemDigitalProductPackageStorage storage;

  @BeforeEach
  void setUp() {
    storage = new FileSystemDigitalProductPackageStorage(
      mediaDirectory.toString()
    );
  }

  @Test
  void shouldStoreAndReadPackageWithoutChangingItsContent()
    throws Exception {

    byte[] content = sampleZip();

    String key = storage.store(content);

    assertThat(key).matches(
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.zip"
    );

    Path expectedPath = mediaDirectory
      .resolve("private")
      .resolve("digital-packages")
      .resolve(key);

    assertThat(Files.isRegularFile(expectedPath)).isTrue();

    try (var input = storage.load(key).getInputStream()) {
      assertThat(input.readAllBytes()).isEqualTo(content);
    }
  }

  @Test
  void shouldNotOverwriteAnExistingPackage() throws Exception {
    byte[] content = sampleZip();

    String firstKey = storage.store(content);
    String secondKey = storage.store(content);

    assertThat(secondKey).isNotEqualTo(firstKey);

    try (var first = storage.load(firstKey).getInputStream();
         var second = storage.load(secondKey).getInputStream()) {
      assertThat(first.readAllBytes()).isEqualTo(content);
      assertThat(second.readAllBytes()).isEqualTo(content);
    }
  }

  @Test
  void shouldDeleteOnlyTheSelectedPackage() throws Exception {
    String firstKey = storage.store(sampleZip());
    String secondKey = storage.store(sampleZip());

    storage.delete(firstKey);

    assertThatThrownBy(() -> storage.load(firstKey))
      .isInstanceOf(NoSuchFileException.class);

    assertThat(storage.load(secondKey).exists()).isTrue();
  }

  @Test
  void shouldAllowDeletingAnAlreadyMissingPackage() throws Exception {
    storage.delete(UUID.randomUUID() + ".zip");
  }

  @Test
  void shouldRejectEmptyContent() {
    assertThatThrownBy(() -> storage.store(new byte[0]))
      .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> storage.store(null))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectInvalidKeysWithoutTouchingOtherFiles()
    throws Exception {

    Path unrelatedFile = mediaDirectory.resolve("keep.txt");
    Files.writeString(unrelatedFile, "Keep this file");

    for (String key : new String[] {
      "../keep.txt",
      "../../keep.txt",
      unrelatedFile.toAbsolutePath().toString(),
      "package.zip",
      UUID.randomUUID() + ".png"
    }) {
      assertThatThrownBy(() -> storage.load(key))
        .isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> storage.delete(key))
        .isInstanceOf(IllegalArgumentException.class);
    }

    assertThat(Files.readString(unrelatedFile))
      .isEqualTo("Keep this file");
  }

  private byte[] sampleZip() throws Exception {
    var output = new ByteArrayOutputStream();

    try (var zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("instructions.txt"));
      zip.write("Test package".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }

    return output.toByteArray();
  }
}
