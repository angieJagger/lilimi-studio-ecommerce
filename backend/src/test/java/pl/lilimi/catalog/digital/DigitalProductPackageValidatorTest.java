package pl.lilimi.catalog.digital;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DigitalProductPackageValidatorTest {

  private final DigitalProductPackageValidator validator =
    new DigitalProductPackageValidator();

  @Test
  void shouldAcceptEmbroideryPackageAndCalculateChecksum()
    throws Exception {

    byte[] content = zip(Map.of(
      "patterns/dragon.dst", bytes("Test embroidery data"),
      "instructions.txt", bytes("Test instructions")
    ));

    var result = validator.validate(upload("dragon.zip", content));

    assertThat(result.originalFilename()).isEqualTo("dragon.zip");
    assertThat(result.byteSize()).isEqualTo(content.length);
    assertThat(result.content()).isEqualTo(content);
    assertThat(result.sha256()).isEqualTo(
      HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(content)
      )
    );
  }

  @Test
  void shouldNormalizeUploadedFilename() throws Exception {
    byte[] content = zip(Map.of("dragon.PES", bytes("Test data")));

    var result = validator.validate(
      upload("C:\\fakepath\\Dragon.ZIP", content)
    );

    assertThat(result.originalFilename()).isEqualTo("Dragon.ZIP");
  }

  @Test
  void shouldRejectMissingOrEmptyUpload() {
    assertThatThrownBy(() -> validator.validate(null))
      .isInstanceOf(InvalidDigitalProductPackageException.class);

    assertThatThrownBy(() ->
      validator.validate(upload("empty.zip", new byte[0]))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectUploadLargerThanTenMiB() {
    byte[] content = new byte[10 * 1024 * 1024 + 1];

    assertThatThrownBy(() ->
      validator.validate(upload("large.zip", content))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectTextRenamedToZip() {
    assertThatThrownBy(() ->
      validator.validate(upload("fake.zip", bytes("Not a ZIP")))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectTruncatedArchive() throws Exception {
    byte[] content = zip(Map.of("dragon.dst", bytes("Test data")));
    byte[] truncated = Arrays.copyOf(content, content.length - 10);

    assertThatThrownBy(() ->
      validator.validate(upload("broken.zip", truncated))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectArchiveWithIncorrectChecksum() throws Exception {
    byte[] content = zip(Map.of("dragon.dst", bytes("Test data")));

    boolean changed = false;

    for (int i = 0; i <= content.length - 46; i++) {
      if (
        content[i] == 0x50 &&
          content[i + 1] == 0x4b &&
          content[i + 2] == 0x01 &&
          content[i + 3] == 0x02
      ) {
        // CRC-32 starts 16 bytes into the central directory header.
        content[i + 16] ^= 1;
        changed = true;
        break;
      }
    }

    assertThat(changed).isTrue();

    assertThatThrownBy(() ->
      validator.validate(upload("broken-crc.zip", content))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "../dragon.dst",
    "/dragon.dst",
    "folder/../../dragon.dst",
    "folder\\dragon.dst",
    "C:/dragon.dst",
    "folder//dragon.dst"
  })
  void shouldRejectUnsafeEntryPaths(String entryName)
    throws Exception {

    byte[] content = zip(Map.of(entryName, bytes("Test data")));

    assertThatThrownBy(() ->
      validator.validate(upload("unsafe.zip", content))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectUnsupportedFilesEvenWithValidEmbroidery()
    throws Exception {

    byte[] content = zip(Map.of(
      "dragon.dst", bytes("Test data"),
      "program.exe", bytes("Unexpected file")
    ));

    assertThatThrownBy(() ->
      validator.validate(upload("unsupported.zip", content))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectPackageWithoutNonEmptyEmbroidery()
    throws Exception {

    byte[] content = zip(Map.of(
      "dragon.dst", new byte[0],
      "instructions.txt", bytes("Instructions only")
    ));

    assertThatThrownBy(() ->
      validator.validate(upload("no-pattern.zip", content))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectDuplicateNamesIgnoringCase() throws Exception {
    byte[] content = zip(Map.of(
      "dragon.dst", bytes("First file"),
      "DRAGON.DST", bytes("Second file")
    ));

    assertThatThrownBy(() ->
      validator.validate(upload("duplicates.zip", content))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldRejectMoreThanOneHundredEntries() throws Exception {
    var output = new ByteArrayOutputStream();

    try (var zip = new ZipOutputStream(output)) {
      for (int i = 0; i < 101; i++) {
        zip.putNextEntry(new ZipEntry("pattern-" + i + ".dst"));
        zip.write(1);
        zip.closeEntry();
      }
    }

    assertThatThrownBy(() ->
      validator.validate(upload("many-files.zip", output.toByteArray()))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  @Test
  void shouldLimitActualUncompressedContent() throws Exception {
    var output = new ByteArrayOutputStream();

    try (var zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("large.dst"));

      byte[] block = new byte[8192];

      for (int i = 0; i < (50 * 1024 * 1024) / block.length; i++) {
        zip.write(block);
      }

      zip.write(1);
      zip.closeEntry();
    }

    byte[] compressed = output.toByteArray();

    assertThat(compressed.length).isLessThan(10 * 1024 * 1024);

    assertThatThrownBy(() ->
      validator.validate(upload("compressed-large.zip", compressed))
    ).isInstanceOf(InvalidDigitalProductPackageException.class);
  }

  private MockMultipartFile upload(String filename, byte[] content) {
    return new MockMultipartFile(
      "file",
      filename,
      "application/zip",
      content
    );
  }

  private byte[] zip(Map<String, byte[]> files) throws Exception {
    var output = new ByteArrayOutputStream();

    try (var zip = new ZipOutputStream(output)) {
      for (var file : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(file.getKey()));
        zip.write(file.getValue());
        zip.closeEntry();
      }
    }

    return output.toByteArray();
  }

  private byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
