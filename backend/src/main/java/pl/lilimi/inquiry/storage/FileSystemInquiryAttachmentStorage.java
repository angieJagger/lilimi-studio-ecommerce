package pl.lilimi.inquiry.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class FileSystemInquiryAttachmentStorage
  implements InquiryAttachmentStorage {

  private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
  private static final long MIN_FREE_SPACE =
    1024L * 1024 * 1024;
  private static final long MAX_TOTAL_SIZE =
    1024L * 1024 * 1024;
  private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.bin"
  );
  private final Path directory;
  private final long maxTotalSize;

  @Autowired
  public FileSystemInquiryAttachmentStorage(
    @Value("${lilimi.media.storage-directory:./var/media}")
    String storageDirectory
  ) {
    this(storageDirectory, MAX_TOTAL_SIZE);
  }

  FileSystemInquiryAttachmentStorage(
    String storageDirectory,
    long maxTotalSize
  ) {
    if (maxTotalSize < 1) {
      throw new IllegalArgumentException(
        "Attachment storage limit must be positive"
      );
    }

    this.directory = Path.of(storageDirectory)
      .toAbsolutePath()
      .normalize()
      .resolve("private")
      .resolve("inquiry-attachments");

    this.maxTotalSize = maxTotalSize;
  }

  @Override
  public synchronized String store(byte[] content) throws IOException {
    if (
      content == null ||
        content.length == 0 ||
        content.length > MAX_FILE_SIZE
    ) {
      throw new IllegalArgumentException(
        "Attachment size must be between 1 byte and 10 MiB"
      );
    }

    Files.createDirectories(directory);
    checkStorageCapacity(content.length);

    long usableSpace = Files.getFileStore(directory).getUsableSpace();

    if (usableSpace < MIN_FREE_SPACE + content.length) {
      throw new IOException(
        "Insufficient disk space for an inquiry attachment"
      );
    }

    String storageKey = UUID.randomUUID() + ".bin";
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

  @Override
  public List<String> findStoredBefore(Instant cutoff) throws IOException {
    var storageKeys = new ArrayList<String>();

    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      return storageKeys;
    }

    try (var entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        String storageKey = entry.getFileName().toString();

        if (!STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
          continue;
        }

        BasicFileAttributes attributes;

        try {
          attributes = Files.readAttributes(
            entry,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
          );
        } catch (NoSuchFileException exception) {
          continue;
        }

        if (
          attributes.isRegularFile() &&
            attributes.lastModifiedTime().toInstant().isBefore(cutoff)
        ) {
          storageKeys.add(storageKey);
        }
      }
    }

    return List.copyOf(storageKeys);
  }

  private void checkStorageCapacity(long incomingBytes) throws IOException {
    long remaining = maxTotalSize - incomingBytes;

    if (remaining < 0) {
      throw new IOException(
        "Inquiry attachment storage limit exceeded"
      );
    }

    try (var entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        BasicFileAttributes attributes;

        try {
          attributes = Files.readAttributes(
            entry,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
          );
        } catch (NoSuchFileException exception) {
          continue;
        }

        if (!attributes.isRegularFile()) {
          continue;
        }

        if (attributes.size() > remaining) {
          throw new IOException(
            "Inquiry attachment storage limit exceeded"
          );
        }

        remaining -= attributes.size();
      }
    }
  }

  private Path resolve(String storageKey) {
    if (
      storageKey == null ||
        !STORAGE_KEY_PATTERN.matcher(storageKey).matches()
    ) {
      throw new IllegalArgumentException(
        "Invalid attachment storage key"
      );
    }

    return directory.resolve(storageKey);
  }
}
