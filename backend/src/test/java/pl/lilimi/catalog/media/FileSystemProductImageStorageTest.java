package pl.lilimi.catalog.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemProductImageStorageTest {

  @TempDir
  Path temporaryDirectory;

  private Path mediaDirectory;
  private FileSystemProductImageStorage storage;

  @BeforeEach
  void setUp() {
    mediaDirectory = temporaryDirectory.resolve("media");
    storage = new FileSystemProductImageStorage(
      mediaDirectory.toString()
    );
  }

  @Test
  void shouldStoreAndLoadContentUsingGeneratedKey() throws IOException {
    byte[] content = { 1, 2, 3, 4 };

    String key = storage.store(content, "png");

    assertTrue(key.matches(
      "[0-9a-f-]{36}\\.png"
    ));
    assertArrayEquals(
      content,
      storage.load(key).getContentAsByteArray()
    );
  }

  @Test
  void shouldGenerateDifferentKeysForSeparateUploads() throws IOException {
    byte[] content = { 1, 2, 3 };

    String firstKey = storage.store(content, "jpg");
    String secondKey = storage.store(content, "jpg");

    assertNotEquals(firstKey, secondKey);
    assertTrue(Files.exists(mediaDirectory.resolve(firstKey)));
    assertTrue(Files.exists(mediaDirectory.resolve(secondKey)));
  }

  @Test
  void shouldDeleteStoredFileAndAllowRepeatedDeletion() throws IOException {
    String key = storage.store(new byte[] { 1 }, "webp");

    storage.delete(key);
    storage.delete(key);

    assertFalse(Files.exists(mediaDirectory.resolve(key)));
    assertThrows(
      NoSuchFileException.class,
      () -> storage.load(key)
    );
  }

  @Test
  void shouldRejectPathsOutsideStorageDirectory() throws IOException {
    Path outsideFile = temporaryDirectory.resolve("outside.png");
    Files.write(outsideFile, new byte[] { 7 });

    assertThrows(
      IllegalArgumentException.class,
      () -> storage.load("../outside.png")
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> storage.delete("../outside.png")
    );

    assertArrayEquals(
      new byte[] { 7 },
      Files.readAllBytes(outsideFile)
    );
  }

  @Test
  void shouldRejectEmptyContentAndUnsupportedExtension() {
    assertThrows(
      IllegalArgumentException.class,
      () -> storage.store(new byte[0], "png")
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> storage.store(new byte[] { 1 }, "svg")
    );

    assertFalse(Files.exists(mediaDirectory));
  }

  @Test
  void shouldReportMissingFile() {
    assertThrows(
      NoSuchFileException.class,
      () -> storage.load(
        "00000000-0000-0000-0000-000000000001.jpg"
      )
    );
  }
}
