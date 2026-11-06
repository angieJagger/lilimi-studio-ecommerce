package pl.lilimi.inquiry.application;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.media.ProductImageValidator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryAttachmentValidatorTest {

  private final InquiryAttachmentValidator validator =
    new InquiryAttachmentValidator(new ProductImageValidator());

  @Test
  void shouldAcceptPngAndRemoveClientPath() throws Exception {
    var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();

    ImageIO.write(image, "png", output);
    image.flush();

    var file = new MockMultipartFile(
      "files",
      "C:\\uploads\\logo.png",
      "application/octet-stream",
      output.toByteArray()
    );

    var result = validator.validate(file);

    assertThat(result.originalFilename()).isEqualTo("logo.png");
    assertThat(result.contentType()).isEqualTo("image/png");
    assertThat(result.content()).isEqualTo(output.toByteArray());
  }

  @Test
  void shouldRejectFilenameExtensionDifferentFromImageFormat() throws Exception {
    var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();

    ImageIO.write(image, "png", output);
    image.flush();

    var file = new MockMultipartFile(
      "files",
      "logo.jpg",
      "image/jpeg",
      output.toByteArray()
    );

    assertThatThrownBy(() -> validator.validate(file))
      .isInstanceOf(InvalidInquiryAttachmentException.class);
  }

  @Test
  void shouldAcceptReadablePdf() throws Exception {
    var output = new ByteArrayOutputStream();

    try (var document = new PDDocument()) {
      document.addPage(new PDPage());
      document.save(output);
    }

    var file = new MockMultipartFile(
      "files",
      "inspiration.pdf",
      "application/pdf",
      output.toByteArray()
    );

    var result = validator.validate(file);

    assertThat(result.originalFilename()).isEqualTo("inspiration.pdf");
    assertThat(result.contentType()).isEqualTo("application/pdf");
    assertThat(result.content()).isEqualTo(output.toByteArray());
  }

  @Test
  void shouldRejectFakePdfDespiteItsNameAndContentType() {
    var file = new MockMultipartFile(
      "files",
      "inspiration.pdf",
      "application/pdf",
      "%PDF-1.7\nThis is not a PDF document."
        .getBytes(StandardCharsets.UTF_8)
    );

    assertThatThrownBy(() -> validator.validate(file))
      .isInstanceOf(InvalidInquiryAttachmentException.class);
  }

  @Test
  void shouldRejectAttachmentExceedingSizeLimit() {
    var file = new MockMultipartFile(
      "files",
      "logo.png",
      "image/png",
      new byte[10 * 1024 * 1024 + 1]
    );

    assertThatThrownBy(() -> validator.validate(file))
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> assertThat(exception.getStatusCode())
          .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
      );
  }
}
