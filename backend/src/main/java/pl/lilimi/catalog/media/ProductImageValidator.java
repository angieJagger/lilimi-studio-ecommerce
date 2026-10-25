package pl.lilimi.catalog.media;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Component
public class ProductImageValidator {

  private static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final int MAX_DIMENSION = 8000;
  private static final long MAX_PIXELS = 20_000_000;

  public ValidatedProductImage validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Image file is required"
      );
    }

    if (file.getSize() > MAX_BYTES) {
      throw tooLarge();
    }

    try {
      byte[] content;

      try (var input = file.getInputStream()) {
        content = input.readNBytes(MAX_BYTES + 1);
      }

      if (content.length > MAX_BYTES) {
        throw tooLarge();
      }

      try (var input = new MemoryCacheImageInputStream(
        new ByteArrayInputStream(content)
      )) {
        var readers = ImageIO.getImageReaders(input);

        if (!readers.hasNext()) {
          throw unsupportedFormat();
        }

        var reader = readers.next();

        try {
          reader.setInput(input, true, true);

          String format = reader.getFormatName()
            .toLowerCase(Locale.ROOT);

          String extension = switch (format) {
            case "jpeg", "jpg" -> "jpg";
            case "png" -> "png";
            case "webp" -> "webp";
            default -> throw unsupportedFormat();
          };

          int width = reader.getWidth(0);
          int height = reader.getHeight(0);

          if (
            width < 1
              || height < 1
              || width > MAX_DIMENSION
              || height > MAX_DIMENSION
              || (long) width * height > MAX_PIXELS
          ) {
            throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Image dimensions exceed the allowed limits"
            );
          }

          var decoded = reader.read(0);

          if (decoded == null) {
            throw invalidImage();
          }

          decoded.flush();

          String contentType = switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "image/webp";
          };

          return new ValidatedProductImage(
            content,
            extension,
            contentType,
            width,
            height
          );
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException | IllegalArgumentException exception) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Image file could not be decoded",
        exception
      );
    }
  }

  private ResponseStatusException tooLarge() {
    return new ResponseStatusException(
      HttpStatus.PAYLOAD_TOO_LARGE,
      "Image file must not exceed 10 MB"
    );
  }

  private ResponseStatusException unsupportedFormat() {
    return new ResponseStatusException(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE,
      "Only JPEG, PNG and WebP images are supported"
    );
  }

  private ResponseStatusException invalidImage() {
    return new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Invalid image file"
    );
  }
}
