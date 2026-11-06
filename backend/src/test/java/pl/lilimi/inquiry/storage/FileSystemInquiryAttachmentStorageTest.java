package pl.lilimi.inquiry.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemInquiryAttachmentStorageTest {

  @TempDir
  Path storageDirectory;

  @Test
  void shouldReturnOnlyFilesOlderThanCutoff() throws Exception {
    var storage = new FileSystemInquiryAttachmentStorage(
      storageDirectory.toString()
    );

    String oldKey = storage.store(new byte[] { 1 });
    String recentKey = storage.store(new byte[] { 2 });

    Path directory = storageDirectory
      .resolve("private")
      .resolve("inquiry-attachments");

    Instant cutoff = Instant.parse("2026-11-05T12:00:00Z");

    Files.setLastModifiedTime(
      directory.resolve(oldKey),
      FileTime.from(cutoff.minusSeconds(1))
    );

    Files.setLastModifiedTime(
      directory.resolve(recentKey),
      FileTime.from(cutoff.plusSeconds(1))
    );

    assertThat(storage.findStoredBefore(cutoff))
      .containsExactly(oldKey);
  }

  @Test
  void shouldStoreAndLoadAttachmentFromPrivateDirectory() throws Exception {
    var storage = new FileSystemInquiryAttachmentStorage(
      storageDirectory.toString()
    );

    byte[] content = "attachment".getBytes(StandardCharsets.UTF_8);

    String key = storage.store(content);

    assertThat(key).matches(
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.bin"
    );

    Path storedFile = storageDirectory
      .resolve("private")
      .resolve("inquiry-attachments")
      .resolve(key);

    assertThat(storedFile).exists();

    try (var input = storage.load(key).getInputStream()) {
      assertThat(input.readAllBytes()).isEqualTo(content);
    }
  }

  @Test
  void shouldIgnoreFilesWithoutGeneratedStorageKeys() throws Exception {
    var storage = new FileSystemInquiryAttachmentStorage(
      storageDirectory.toString()
    );

    Path directory = storageDirectory
      .resolve("private")
      .resolve("inquiry-attachments");

    Files.createDirectories(directory);

    Path unrelatedFile = directory.resolve("notes.txt");
    Files.writeString(unrelatedFile, "Keep this file");

    Instant cutoff = Instant.parse("2026-11-05T12:00:00Z");

    Files.setLastModifiedTime(
      unrelatedFile,
      FileTime.from(cutoff.minusSeconds(1))
    );

    assertThat(storage.findStoredBefore(cutoff)).isEmpty();
    assertThat(unrelatedFile).exists();
  }

  @Test
  void shouldDeleteAttachmentAndAllowRepeatedDeletion() throws Exception {
    var storage = new FileSystemInquiryAttachmentStorage(
      storageDirectory.toString()
    );

    String key = storage.store(new byte[] { 1, 2, 3 });

    storage.delete(key);
    storage.delete(key);

    assertThatThrownBy(() -> storage.load(key))
      .isInstanceOf(NoSuchFileException.class);
  }

  @Test
  void shouldRejectPathTraversalForLoadAndDelete() throws Exception {
    var storage = new FileSystemInquiryAttachmentStorage(
      storageDirectory.toString()
    );

    Path outsideFile = storageDirectory.resolve("outside.bin");
    Files.write(outsideFile, new byte[] { 1, 2, 3 });

    String invalidKey = "../../outside.bin";

    assertThatThrownBy(() -> storage.load(invalidKey))
      .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> storage.delete(invalidKey))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(outsideFile).hasBinaryContent(new byte[] { 1, 2, 3 });
  }
}
