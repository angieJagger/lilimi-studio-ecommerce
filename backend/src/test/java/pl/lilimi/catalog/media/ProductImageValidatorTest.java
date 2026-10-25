package pl.lilimi.catalog.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class ProductImageValidatorTest {

  private final ProductImageValidator validator =
    new ProductImageValidator();

  @ParameterizedTest
  @CsvSource({
    "png, png, image/png",
    "jpeg, jpg, image/jpeg"
  })
  void shouldDetectActualFormatDespiteMisleadingFileMetadata(
    String format,
    String extension,
    String contentType
  ) throws IOException {
    byte[] content = createImage(format, 4, 3);

    var file = new MockMultipartFile(
      "file",
      "misleading-name.txt",
      "text/plain",
      content
    );

    var result = validator.validate(file);

    assertEquals(extension, result.extension());
    assertEquals(contentType, result.contentType());
    assertEquals(4, result.width());
    assertEquals(3, result.height());
    assertArrayEquals(content, result.content());
  }

  @Test
  void shouldRejectMissingAndEmptyFiles() {
    assertStatus(
      HttpStatus.BAD_REQUEST,
      () -> validator.validate(null)
    );

    assertStatus(
      HttpStatus.BAD_REQUEST,
      () -> validator.validate(new MockMultipartFile(
        "file",
        new byte[0]
      ))
    );
  }

  @Test
  void shouldRejectTextDisguisedAsJpeg() {
    var file = new MockMultipartFile(
      "file",
      "photo.jpg",
      "image/jpeg",
      "This is not an image".getBytes(StandardCharsets.UTF_8)
    );

    assertStatus(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE,
      () -> validator.validate(file)
    );
  }

  @Test
  void shouldRejectUnsupportedImageFormat() throws IOException {
    var file = new MockMultipartFile(
      "file",
      "photo.gif",
      "image/gif",
      createImage("gif", 2, 2)
    );

    assertStatus(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE,
      () -> validator.validate(file)
    );
  }

  @Test
  void shouldRejectFileLargerThanTenMegabytes() {
    var file = new MockMultipartFile(
      "file",
      "photo.png",
      "image/png",
      new byte[10 * 1024 * 1024 + 1]
    );

    assertStatus(
      HttpStatus.PAYLOAD_TOO_LARGE,
      () -> validator.validate(file)
    );
  }

  @ParameterizedTest
  @CsvSource({
    "8001, 1",
    "1, 8001",
    "6000, 4000"
  })
  void shouldRejectExcessiveDimensionsBeforeDecodingPixels(
    int width,
    int height
  ) throws IOException {
    byte[] content = createImage("png", 1, 1);

    // Change the PNG header without allocating a large image.
    ByteBuffer.wrap(content)
      .putInt(16, width)
      .putInt(20, height);

    var checksum = new CRC32();
    checksum.update(content, 12, 17);
    ByteBuffer.wrap(content).putInt(29, (int) checksum.getValue());

    var file = new MockMultipartFile(
      "file",
      "large.png",
      "image/png",
      content
    );

    var exception = assertStatus(
      HttpStatus.BAD_REQUEST,
      () -> validator.validate(file)
    );

    assertEquals(
      "Image dimensions exceed the allowed limits",
      exception.getReason()
    );
  }

  @Test
  void shouldRejectPngWithMissingPixelData() throws IOException {
    byte[] content = createImage("png", 2, 2);
    byte[] headerOnly = java.util.Arrays.copyOf(content, 33);

    var file = new MockMultipartFile(
      "file",
      "broken.png",
      "image/png",
      headerOnly
    );

    assertStatus(
      HttpStatus.BAD_REQUEST,
      () -> validator.validate(file)
    );
  }

  private byte[] createImage(
    String format,
    int width,
    int height
  ) throws IOException {
    var image = new BufferedImage(
      width,
      height,
      BufferedImage.TYPE_INT_RGB
    );

    try (var output = new ByteArrayOutputStream()) {
      assertTrue(ImageIO.write(image, format, output));
      return output.toByteArray();
    } finally {
      image.flush();
    }
  }

  private ResponseStatusException assertStatus(
    HttpStatus status,
    org.junit.jupiter.api.function.Executable operation
  ) {
    var exception = assertThrows(
      ResponseStatusException.class,
      operation
    );

    assertEquals(status, exception.getStatusCode());

    return exception;
  }
}
