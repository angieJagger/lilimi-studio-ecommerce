package pl.lilimi.catalog.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class FileSystemProductImageStorage
  implements ProductImageStorage {

  private static final Set<String> ALLOWED_EXTENSIONS =
    Set.of("jpg", "png", "webp");

  private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
      + "\\.(jpg|png|webp)"
  );

  private final Path directory;

  public FileSystemProductImageStorage(
    @Value("${lilimi.media.storage-directory:./var/media}")
    String storageDirectory
  ) {
    this.directory = Path.of(storageDirectory)
      .toAbsolutePath()
      .normalize();
  }

  @Override
  public String store(
    byte[] content,
    String extension
  ) throws IOException {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("Image content is required");
    }

    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new IllegalArgumentException("Unsupported image extension");
    }

    Files.createDirectories(directory);

    String storageKey = UUID.randomUUID() + "." + extension;
    Path target = resolve(storageKey);

    var stream = Files.newOutputStream(
      target,
      StandardOpenOption.CREATE_NEW,
      StandardOpenOption.WRITE
    );

    try (var output = stream) {
      output.write(content);
    } catch (IOException exception) {
      try {
        Files.deleteIfExists(target);
      } catch (IOException cleanupException) {
        exception.addSuppressed(cleanupException);
      }

      throw exception;
    }

    return storageKey;
  }

  @Override
  public Resource load(String storageKey) throws IOException {
    Path target = resolve(storageKey);

    if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new NoSuchFileException(storageKey);
    }

    return new UrlResource(target.toUri());
  }

  @Override
  public void delete(String storageKey) throws IOException {
    Files.deleteIfExists(resolve(storageKey));
  }

  private Path resolve(String storageKey) {
    if (
      storageKey == null
        || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()
    ) {
      throw new IllegalArgumentException("Invalid image storage key");
    }

    return directory.resolve(storageKey);
  }
}
