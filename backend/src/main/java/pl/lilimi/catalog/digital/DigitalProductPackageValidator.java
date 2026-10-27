package pl.lilimi.catalog.digital;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class DigitalProductPackageValidator {

  private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
  private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;
  private static final int MAX_ENTRIES = 100;

  private static final Set<String> EMBROIDERY_EXTENSIONS =
    Set.of("dst", "pes", "jef");

  private static final Set<String> ALLOWED_EXTENSIONS =
    Set.of("dst", "pes", "jef", "pdf", "txt");

  public ValidatedDigitalProductPackage validate(
    MultipartFile file
  ) throws IOException {
    if (file == null || file.isEmpty()) {
      throw invalid("Package is required");
    }

    if (file.getSize() > MAX_UPLOAD_BYTES) {
      throw invalid("Package exceeds the upload limit");
    }

    String filename = validateFilename(file.getOriginalFilename());

    byte[] content;
    try (var input = file.getInputStream()) {
      content = input.readNBytes(MAX_UPLOAD_BYTES + 1);
    }

    if (content.length == 0 || content.length > MAX_UPLOAD_BYTES) {
      throw invalid("Invalid package size");
    }

    if (
      content.length < 4 ||
        content[0] != 'P' ||
        content[1] != 'K' ||
        content[2] != 3 ||
        content[3] != 4
    ) {
      throw invalid("File is not a supported ZIP archive");
    }

    Path temporaryFile = Files.createTempFile(
      "lilimi-package-validation-", ".zip"
    );

    try {
      Files.write(temporaryFile, content);
      validateArchive(temporaryFile);
    } finally {
      Files.deleteIfExists(temporaryFile);
    }

    return new ValidatedDigitalProductPackage(
      content,
      filename,
      sha256(content)
    );
  }

  private void validateArchive(Path path) {
    try (var zip = new ZipFile(path.toFile())) {
      if (zip.size() == 0 || zip.size() > MAX_ENTRIES) {
        throw invalid("Invalid number of archive entries");
      }

      var names = new HashSet<String>();
      var entries = zip.entries();

      long totalBytes = 0;
      boolean containsEmbroidery = false;
      byte[] buffer = new byte[8192];

      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        String name = entry.getName();

        validateEntryName(name, entry.isDirectory());

        if (!names.add(name.toLowerCase(Locale.ROOT))) {
          throw invalid("Duplicate archive entry");
        }

        if (entry.isDirectory()) {
          if (entry.getSize() != 0) {
            throw invalid("Directory entry contains data");
          }
          continue;
        }

        String extension = extensionOf(name);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
          throw invalid("Unsupported file inside the package");
        }

        if (
          entry.getMethod() != ZipEntry.STORED &&
            entry.getMethod() != ZipEntry.DEFLATED
        ) {
          throw invalid("Unsupported ZIP compression method");
        }

        if (
          entry.getSize() < 0 ||
            entry.getSize() > MAX_UNCOMPRESSED_BYTES
        ) {
          throw invalid("Invalid uncompressed file size");
        }

        var checksum = new CRC32();
        long entryBytes = 0;

        try (var input = zip.getInputStream(entry)) {
          int read;

          while ((read = input.read(buffer)) != -1) {
            entryBytes += read;
            totalBytes += read;

            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
              throw invalid("Uncompressed package exceeds the limit");
            }

            checksum.update(buffer, 0, read);
          }
        }

        if (
          entryBytes != entry.getSize() ||
            checksum.getValue() != entry.getCrc()
        ) {
          throw invalid("Corrupted archive entry");
        }

        if (
          EMBROIDERY_EXTENSIONS.contains(extension) &&
            entryBytes > 0
        ) {
          containsEmbroidery = true;
        }
      }

      if (!containsEmbroidery) {
        throw invalid("Package must contain an embroidery file");
      }
    } catch (IOException exception) {
      throw new InvalidDigitalProductPackageException(
        "Cannot read ZIP archive",
        exception
      );
    }
  }

  private String validateFilename(String originalFilename) {
    if (originalFilename == null) {
      throw invalid("Filename is required");
    }

    String normalized = originalFilename.replace('\\', '/');
    String filename = normalized
      .substring(normalized.lastIndexOf('/') + 1)
      .trim();

    if (
      filename.isBlank() ||
        filename.length() > 255 ||
        filename.chars().anyMatch(Character::isISOControl) ||
        !filename.toLowerCase(Locale.ROOT).endsWith(".zip")
    ) {
      throw invalid("Invalid ZIP filename");
    }

    return filename;
  }

  private void validateEntryName(String name, boolean directory) {
    if (
      name == null ||
        name.isBlank() ||
        name.length() > 255 ||
        name.startsWith("/") ||
        name.contains("\\") ||
        name.contains(":") ||
        name.chars().anyMatch(Character::isISOControl)
    ) {
      throw invalid("Invalid archive entry name");
    }

    String checkedName = directory && name.endsWith("/")
      ? name.substring(0, name.length() - 1)
      : name;

    for (String segment : checkedName.split("/", -1)) {
      if (
        segment.isBlank() ||
          segment.equals(".") ||
          segment.equals("..")
      ) {
        throw invalid("Unsafe archive entry path");
      }
    }
  }

  private String extensionOf(String name) {
    int dot = name.lastIndexOf('.');
    int slash = name.lastIndexOf('/');

    return dot > slash
      ? name.substring(dot + 1).toLowerCase(Locale.ROOT)
      : "";
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(content)
      );
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
        "SHA-256 is unavailable",
        exception
      );
    }
  }

  private InvalidDigitalProductPackageException invalid(
    String message
  ) {
    return new InvalidDigitalProductPackageException(message);
  }
}
