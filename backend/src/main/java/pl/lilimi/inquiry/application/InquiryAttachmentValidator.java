package pl.lilimi.inquiry.application;

import org.apache.pdfbox.Loader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.catalog.media.ProductImageValidator;
import pl.lilimi.catalog.media.ValidatedProductImage;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Component
public class InquiryAttachmentValidator {

  private static final int MAX_BYTES = 10 * 1024 * 1024;

  private static final Map<String, String> CONTENT_TYPES = Map.of(
    "jpg", "image/jpeg",
    "jpeg", "image/jpeg",
    "png", "image/png",
    "webp", "image/webp",
    "pdf", "application/pdf"
  );

  private final ProductImageValidator images;

  public InquiryAttachmentValidator(ProductImageValidator images) {
    this.images = images;
  }

  public ValidatedInquiryAttachment validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw invalid("Attachment file is required");
    }

    if (file.getSize() > MAX_BYTES) {
      throw tooLarge();
    }

    String filename = safeFilename(file.getOriginalFilename());
    String extension = filename.substring(filename.lastIndexOf('.') + 1)
      .toLowerCase(Locale.ROOT);
    String expectedContentType = CONTENT_TYPES.get(extension);

    if (expectedContentType == null) {
      throw new ResponseStatusException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Allowed attachment formats: JPG, PNG, WebP and PDF"
      );
    }

    if ("pdf".equals(extension)) {
      return validatePdf(file, filename);
    }

    ValidatedProductImage image;

    try {
      image = images.validate(file);
    } catch (ResponseStatusException exception) {
      if (exception.getStatusCode().value() == 400) {
        throw new InvalidInquiryAttachmentException(
          "Attachment image is unreadable or exceeds allowed dimensions",
          exception
        );
      }

      throw exception;
    }

    if (!expectedContentType.equals(image.contentType())) {
      throw invalid("Filename extension does not match the image format");
    }

    return new ValidatedInquiryAttachment(
      filename,
      image.contentType(),
      image.content()
    );
  }

  private ValidatedInquiryAttachment validatePdf(
    MultipartFile file,
    String filename
  ) {
    byte[] content;

    try (var input = file.getInputStream()) {
      content = input.readNBytes(MAX_BYTES + 1);
    } catch (IOException exception) {
      throw new InvalidInquiryAttachmentException(
        "Cannot read attachment",
        exception
      );
    }

    if (content.length > MAX_BYTES) {
      throw tooLarge();
    }

    if (
      content.length < 5 ||
        content[0] != '%' ||
        content[1] != 'P' ||
        content[2] != 'D' ||
        content[3] != 'F' ||
        content[4] != '-'
    ) {
      throw invalid("Attachment is not a PDF document");
    }

    try (var document = Loader.loadPDF(content)) {
      if (document.isEncrypted()) {
        throw invalid("Password-protected PDF documents are not supported");
      }

      if (document.getNumberOfPages() < 1) {
        throw invalid("PDF document must contain at least one page");
      }
    } catch (IOException exception) {
      throw new InvalidInquiryAttachmentException(
        "PDF document is unreadable or requires a password",
        exception
      );
    }

    return new ValidatedInquiryAttachment(
      filename,
      "application/pdf",
      content
    );
  }

  private String safeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw invalid("Attachment filename is required");
    }

    String normalized = originalFilename.replace('\\', '/');
    String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
      .strip();

    if (
      filename.isBlank() ||
        filename.length() > 255 ||
        filename.codePoints().anyMatch(Character::isISOControl) ||
        filename.contains(":") ||
        filename.lastIndexOf('.') <= 0
    ) {
      throw invalid("Invalid attachment filename");
    }

    return filename;
  }

  private InvalidInquiryAttachmentException invalid(String message) {
    return new InvalidInquiryAttachmentException(message);
  }

  private ResponseStatusException tooLarge() {
    return new ResponseStatusException(
      HttpStatus.PAYLOAD_TOO_LARGE,
      "Attachment exceeds the 10 MiB limit"
    );
  }
}
