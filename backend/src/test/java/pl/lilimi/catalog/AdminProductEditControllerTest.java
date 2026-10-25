package pl.lilimi.catalog;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.catalog.admin.api.*;
import pl.lilimi.catalog.media.ProductImageStorage;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false", "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class AdminProductEditControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProductImageRepository images;
  @MockitoBean ProductImageStorage storage;
  private final JsonMapper json = JsonMapper.builder().build();
  private String productId;
  private UUID imageId;
  private String oldKey;
  private final String newKey = "00000000-0000-0000-0000-000000000099.png";

  @BeforeEach void setup() throws Exception {
    clearInvocations(storage);
    productId = "edit-test-" + UUID.randomUUID();
    imageId = UUID.randomUUID();
    oldKey = imageId + ".png";
    jdbc.update("INSERT INTO products(id, slug, product_type, active) VALUES (?, ?, 'digital', true)", productId, productId);
    jdbc.update("INSERT INTO product_translations(product_id, language, name, description) VALUES (?, 'pl', 'Original PL', 'Original description'), (?, 'en', 'Original EN', 'Original description')", productId, productId);
    jdbc.update("INSERT INTO digital_product_prices(product_id, price_in_grosz) VALUES (?, 2900)", productId);
    images.saveAndFlush(new ProductImage(imageId, productId, oldKey, "image/png", 4, 2, 2, 0, true, "Original alt", "Original alt"));
    when(storage.store(any(byte[].class), eq("png"))).thenReturn(newKey);
  }

  @AfterEach void cleanup() {
    jdbc.update("DELETE FROM product_images WHERE product_id = ?", productId);
    jdbc.update("DELETE FROM digital_product_prices WHERE product_id = ?", productId);
    jdbc.update("DELETE FROM product_translations WHERE product_id = ?", productId);
    jdbc.update("DELETE FROM products WHERE id = ?", productId);
  }

  @Test void shouldCommitPriceTranslationsAndGalleryTogether() throws Exception {
    var manifest = List.of(item(imageId, null, false), item(null, 0, true));
    mvc.perform(adminRequest(manifest, 0).file(image()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.product.version").value(1))
      .andExpect(jsonPath("$.product.digitalPriceInGrosz").value(3550))
      .andExpect(jsonPath("$.gallery.images.length()").value(2))
      .andExpect(jsonPath("$.gallery.images[0].primaryImage").value(false))
      .andExpect(jsonPath("$.gallery.images[1].primaryImage").value(true));
    assertThat(price()).isEqualTo(3550);
    assertThat(name()).isEqualTo("Updated EN");
    assertThat(images.findAllByProductIdOrderBySortOrderAscIdAsc(productId)).hasSize(2);
    verify(storage, never()).delete(anyString());
  }

  @Test void shouldCommitRemovalAndDeleteFileOnlyAfterCommit() throws Exception {
    mvc.perform(adminRequest(List.of(), 0)).andExpect(status().isOk())
      .andExpect(jsonPath("$.gallery.images").isEmpty());
    assertThat(images.findById(imageId)).isEmpty();
    verify(storage).delete(oldKey);
    assertThat(price()).isEqualTo(3550);
  }

  @Test void shouldReplaceFileWithoutChangingImageIdentity() throws Exception {
    mvc.perform(adminRequest(List.of(item(imageId, 0, true)), 0).file(image()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.gallery.images[0].id").value(imageId.toString()));
    assertThat(images.findById(imageId).orElseThrow().getStorageKey()).isEqualTo(newKey);
    verify(storage).delete(oldKey);
    verify(storage, never()).delete(newKey);
  }

  @Test void shouldRollBackEveryChangeAndNewFilesWhenSecondFileCannotBeStored() throws Exception {
    when(storage.store(any(byte[].class), eq("png"))).thenReturn(newKey).thenThrow(new IOException("Disk unavailable"));
    mvc.perform(adminRequest(List.of(item(null, 0, true), item(null, 1, false)), 0)
      .file(image()).file(image())).andExpect(status().isInternalServerError());
    assertOriginalProduct();
    verify(storage).delete(newKey);
    verify(storage, never()).delete(oldKey);
  }

  @Test void shouldRejectInvalidImageWithoutSavingOtherFields() throws Exception {
    mvc.perform(adminRequest(List.of(item(null, 0, true)), 0)
      .file(new MockMultipartFile("files", "fake.png", "image/png", new byte[] {1, 2, 3})))
      .andExpect(status().isUnsupportedMediaType());
    assertOriginalProduct();
    verifyNoInteractions(storage);
  }

  @Test void shouldRejectStaleVersionWithoutSavingAnyChanges() throws Exception {
    mvc.perform(adminRequest(List.of(item(imageId, null, true)), 1))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_VERSION_CONFLICT"));
    assertOriginalProduct(); verifyNoInteractions(storage);
  }

  @Test void shouldRejectForeignImageAndMultiplePrimaryImages() throws Exception {
    mvc.perform(adminRequest(List.of(item(UUID.randomUUID(), null, true)), 0)).andExpect(status().isBadRequest());
    mvc.perform(adminRequest(List.of(item(imageId, null, true), item(null, 0, true)), 0).file(image()))
      .andExpect(status().isBadRequest());
    assertOriginalProduct(); verifyNoInteractions(storage);
  }

  @Test void shouldRejectUnusedFilesAndDuplicateFileIndexes() throws Exception {
    mvc.perform(adminRequest(List.of(item(imageId, null, true)), 0).file(image())).andExpect(status().isBadRequest());
    mvc.perform(adminRequest(List.of(item(null, 0, true), item(null, 0, false)), 0).file(image())).andExpect(status().isBadRequest());
    assertOriginalProduct(); verifyNoInteractions(storage);
  }

  @Test void shouldRequireAdministratorAndCsrf() throws Exception {
    var metadata = metadata(List.of(item(imageId, null, true)), 0);
    mvc.perform(multipart("/api/admin/products/{id}/edit", productId).file(metadata).with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(multipart("/api/admin/products/{id}/edit", productId).file(metadata).with(user("customer@example.com").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
    mvc.perform(multipart("/api/admin/products/{id}/edit", productId).file(metadata).with(user("admin@example.com").roles("ADMIN"))).andExpect(status().isForbidden());
    assertOriginalProduct(); verifyNoInteractions(storage);
  }

  private SaveProductImageRequest item(UUID id, Integer fileIndex, boolean primary) {
    return new SaveProductImageRequest(id, fileIndex, "Updated PL alt", "Updated EN alt", primary);
  }
  private MockMultipartHttpServletRequestBuilder adminRequest(List<SaveProductImageRequest> manifest, long version) throws Exception {
    var builder = multipart("/api/admin/products/{id}/edit", productId).file(metadata(manifest, version));
    builder.with(user("admin@example.com").roles("ADMIN")).with(csrf());
    return builder;
  }
  private MockMultipartFile metadata(List<SaveProductImageRequest> manifest, long version) {
    var request = new SaveProductRequest(version,
      new UpdateProductTranslationRequest("Updated PL", "Updated description"),
      new UpdateProductTranslationRequest("Updated EN", "Updated description"), 3550, manifest);
    return new MockMultipartFile("metadata", "", "application/json", json.writeValueAsBytes(request));
  }
  private MockMultipartFile image() throws Exception {
    var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    try (var output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", output);
      return new MockMultipartFile("files", "new.png", "image/png", output.toByteArray());
    } finally { image.flush(); }
  }
  private Integer price() { return jdbc.queryForObject("SELECT price_in_grosz FROM digital_product_prices WHERE product_id = ?", Integer.class, productId); }
  private String name() { return jdbc.queryForObject("SELECT name FROM product_translations WHERE product_id = ? AND language = 'en'", String.class, productId); }
  private void assertOriginalProduct() {
    assertThat(price()).isEqualTo(2900);
    assertThat(name()).isEqualTo("Original EN");
    assertThat(jdbc.queryForObject("SELECT version FROM products WHERE id = ?", Long.class, productId)).isZero();
    var saved = images.findAllByProductIdOrderBySortOrderAscIdAsc(productId);
    assertThat(saved).hasSize(1);
    assertThat(saved.getFirst().getStorageKey()).isEqualTo(oldKey);
    assertThat(saved.getFirst().isPrimaryImage()).isTrue();
  }
}
