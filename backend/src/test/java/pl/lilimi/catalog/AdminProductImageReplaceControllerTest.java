package pl.lilimi.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.catalog.media.ProductImageStorage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductImageReplaceControllerTest {

  private static final String PRODUCT_ID = "pattern-001";
  private static final String URL =
    "/api/admin/products/{productId}/images/{imageId}/file";
  private static final String NEW_STORAGE_KEY =
    "00000000-0000-0000-0000-000000000099.png";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private ProductImageRepository images;

  @MockitoBean
  private ProductImageStorage storage;

  private ProductImage image;
  private String oldStorageKey;

  @BeforeEach
  void setUp() throws Exception {
    clearInvocations(storage);

    UUID id = UUID.randomUUID();
    oldStorageKey = id + ".jpg";

    image = images.saveAndFlush(new ProductImage(
      id,
      PRODUCT_ID,
      oldStorageKey,
      "image/jpeg",
      100,
      2,
      2,
      3,
      true,
      "Haft kwiatowy",
      "Floral embroidery"
    ));

    when(storage.store(any(byte[].class), eq("png")))
      .thenReturn(NEW_STORAGE_KEY);
  }

  @Test
  void shouldReplaceFileAndPreserveImageIdentityAndMetadata()
    throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        multipart(URL, PRODUCT_ID, image.getId())
          .file(replacement())
          .file(metadata(version))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(version + 1)
      ))
      .andExpect(jsonPath("$.images.length()").value(1))
      .andExpect(jsonPath("$.images[0].id").value(
        image.getId().toString()
      ))
      .andExpect(jsonPath("$.images[0].contentType").value("image/png"))
      .andExpect(jsonPath("$.images[0].width").value(5))
      .andExpect(jsonPath("$.images[0].height").value(4))
      .andExpect(jsonPath("$.images[0].sortOrder").value(3))
      .andExpect(jsonPath("$.images[0].primaryImage").value(true))
      .andExpect(jsonPath("$.images[0].altPl").value("Haft kwiatowy"))
      .andExpect(jsonPath("$.images[0].altEn").value("Floral embroidery"));

    var saved = images.findById(image.getId()).orElseThrow();

    assertThat(saved.getStorageKey()).isEqualTo(NEW_STORAGE_KEY);
    assertThat(saved.getByteSize())
      .isEqualTo(replacement().getSize());

    // Neither file is deleted before the transaction completes.
    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldRejectStaleVersionWithoutStoringReplacement()
    throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        multipart(URL, PRODUCT_ID, image.getId())
          .file(replacement())
          .file(metadata(version + 1))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    assertOriginalFile();
    assertThat(currentVersion()).isEqualTo(version);
    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectImageBelongingToAnotherProduct() throws Exception {
    long version = products.findById("pattern-002")
      .orElseThrow()
      .getVersion();

    mockMvc.perform(
        multipart(URL, "pattern-002", image.getId())
          .file(replacement())
          .file(metadata(version))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNotFound());

    assertOriginalFile();
    verifyNoInteractions(storage);
  }

  @Test
  void shouldReturnNotFoundForMissingImage() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID, UUID.randomUUID())
          .file(replacement())
          .file(metadata(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNotFound());

    assertOriginalFile();
    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID, image.getId())
          .file(replacement())
          .file(metadata(currentVersion()))
          .with(csrf())
      )
      .andExpect(status().isUnauthorized());

    assertOriginalFile();
    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID, image.getId())
          .file(replacement())
          .file(metadata(currentVersion()))
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
      )
      .andExpect(status().isForbidden());

    assertOriginalFile();
    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID, image.getId())
          .file(replacement())
          .file(metadata(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isForbidden());

    assertOriginalFile();
    verifyNoInteractions(storage);
  }

  @Test
  void shouldDeleteOnlyNewFileWhenTransactionRollsBack()
    throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID, image.getId())
          .file(replacement())
          .file(metadata(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isOk());

    TestTransaction.flagForRollback();
    TestTransaction.end();

    verify(storage).delete(NEW_STORAGE_KEY);
    verify(storage, never()).delete(oldStorageKey);
  }

  private void assertOriginalFile() {
    var saved = images.findById(image.getId()).orElseThrow();

    assertThat(saved.getStorageKey()).isEqualTo(oldStorageKey);
    assertThat(saved.getContentType()).isEqualTo("image/jpeg");
    assertThat(saved.getWidth()).isEqualTo(2);
    assertThat(saved.getHeight()).isEqualTo(2);
  }

  private long currentVersion() {
    return products.findById(PRODUCT_ID)
      .orElseThrow()
      .getVersion();
  }

  private MockMultipartFile metadata(long version) {
    String json = """
      {"expectedVersion": %d}
      """.formatted(version);

    return new MockMultipartFile(
      "metadata",
      "",
      MediaType.APPLICATION_JSON_VALUE,
      json.getBytes(StandardCharsets.UTF_8)
    );
  }

  private MockMultipartFile replacement() throws Exception {
    var replacement = new BufferedImage(
      5,
      4,
      BufferedImage.TYPE_INT_RGB
    );

    try (var output = new ByteArrayOutputStream()) {
      assertThat(ImageIO.write(replacement, "png", output)).isTrue();

      return new MockMultipartFile(
        "file",
        "replacement.png",
        MediaType.IMAGE_PNG_VALUE,
        output.toByteArray()
      );
    } finally {
      replacement.flush();
    }
  }
}
