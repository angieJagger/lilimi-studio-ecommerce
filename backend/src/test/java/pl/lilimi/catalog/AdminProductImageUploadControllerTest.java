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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
class AdminProductImageUploadControllerTest {

  private static final String PRODUCT_ID = "pattern-001";
  private static final String URL =
    "/api/admin/products/{productId}/images";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private ProductImageRepository images;

  @MockitoBean
  private ProductImageStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    clearInvocations(storage);
    when(storage.store(any(byte[].class), eq("png")))
      .thenAnswer(invocation -> UUID.randomUUID() + ".png");
  }

  @Test
  void shouldUploadFirstImageAsPrimaryAndIncreaseVersion()
    throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        multipart(URL, PRODUCT_ID)
          .file(image())
          .file(metadata(version))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(version + 1)
      ))
      .andExpect(jsonPath("$.images.length()").value(1))
      .andExpect(jsonPath("$.images[0].primaryImage").value(true))
      .andExpect(jsonPath("$.images[0].sortOrder").value(0))
      .andExpect(jsonPath("$.images[0].contentType").value("image/png"))
      .andExpect(jsonPath("$.images[0].width").value(4))
      .andExpect(jsonPath("$.images[0].height").value(3))
      .andExpect(jsonPath("$.images[0].altPl").value("Haft kwiatowy"))
      .andExpect(jsonPath("$.images[0].altEn").value("Floral embroidery"))
      .andExpect(jsonPath("$.images[0].storageKey").doesNotExist());

    var saved = images
      .findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID);

    assertThat(saved).hasSize(1);
    assertThat(saved.getFirst().isPrimaryImage()).isTrue();

    verify(storage).store(any(byte[].class), eq("png"));
  }

  @Test
  void shouldRejectStaleVersionWithoutStoringFile() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID)
          .file(image())
          .file(metadata(currentVersion() + 1))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    verifyNoInteractions(storage);
    assertNoImages();
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID)
          .file(image())
          .file(metadata(currentVersion()))
          .with(csrf())
      )
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(storage);
    assertNoImages();
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID)
          .file(image())
          .file(metadata(currentVersion()))
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
      )
      .andExpect(status().isForbidden());

    verifyNoInteractions(storage);
    assertNoImages();
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID)
          .file(image())
          .file(metadata(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isForbidden());

    verifyNoInteractions(storage);
    assertNoImages();
  }

  @Test
  void shouldReturnNotFoundForMissingProduct() throws Exception {
    mockMvc.perform(
        multipart(URL, "missing-product")
          .file(image())
          .file(metadata(0))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNotFound());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldDeleteStoredFileWhenTransactionRollsBack()
    throws Exception {
    mockMvc.perform(
        multipart(URL, PRODUCT_ID)
          .file(image())
          .file(metadata(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isCreated());

    String storageKey = images
      .findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)
      .getFirst()
      .getStorageKey();

    TestTransaction.flagForRollback();
    TestTransaction.end();

    verify(storage).delete(storageKey);
    assertNoImages();
  }

  private long currentVersion() {
    return products.findById(PRODUCT_ID)
      .orElseThrow()
      .getVersion();
  }

  private void assertNoImages() {
    assertThat(
      images.findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)
    ).isEmpty();
  }

  private MockMultipartFile metadata(long version) {
    String json = """
      {
        "altPl": "Haft kwiatowy",
        "altEn": "Floral embroidery",
        "expectedVersion": %d
      }
      """.formatted(version);

    return new MockMultipartFile(
      "metadata",
      "",
      MediaType.APPLICATION_JSON_VALUE,
      json.getBytes(StandardCharsets.UTF_8)
    );
  }

  private MockMultipartFile image() throws Exception {
    var image = new BufferedImage(
      4,
      3,
      BufferedImage.TYPE_INT_RGB
    );

    try (var output = new ByteArrayOutputStream()) {
      assertThat(ImageIO.write(image, "png", output)).isTrue();

      return new MockMultipartFile(
        "file",
        "photo.png",
        MediaType.IMAGE_PNG_VALUE,
        output.toByteArray()
      );
    } finally {
      image.flush();
    }
  }
}
