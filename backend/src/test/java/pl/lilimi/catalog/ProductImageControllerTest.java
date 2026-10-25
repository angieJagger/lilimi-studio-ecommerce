package pl.lilimi.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.catalog.media.ProductImageStorage;

import java.nio.file.NoSuchFileException;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false", "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class ProductImageControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ProductImageRepository images;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @MockitoBean ProductImageStorage storage;
  private ProductImage primary;
  private ProductImage secondary;
  private final byte[] bytes = {1, 2, 3, 4};
  private static final String URL = "/api/products/{slug}/images/{imageId}/content";

  @BeforeEach void setup() throws Exception {
    clearInvocations(storage);
    secondary = image(0, false, "Secondary PL", "Secondary EN");
    primary = image(3, true, "Główne zdjęcie", "Primary image");
    when(storage.load(primary.getStorageKey())).thenReturn(new ByteArrayResource(bytes));
  }

  @Test void shouldExposeGalleryWithPrimaryFirstAndLocalizedAltWithoutStorageKeys() throws Exception {
    mvc.perform(get("/api/products/forest-dragon")).andExpect(status().isOk())
      .andExpect(jsonPath("$.images.length()").value(2))
      .andExpect(jsonPath("$.images[0].id").value(primary.getId().toString()))
      .andExpect(jsonPath("$.images[1].id").value(secondary.getId().toString()))
      .andExpect(jsonPath("$.images[0].alt.pl").value("Główne zdjęcie"))
      .andExpect(jsonPath("$.images[0].alt.en").value("Primary image"))
      .andExpect(jsonPath("$.images[0].version").isNumber())
      .andExpect(jsonPath("$.images[0].storageKey").doesNotExist());
    mvc.perform(get("/api/products")).andExpect(status().isOk())
      .andExpect(jsonPath("$[?(@.id == 'pattern-001')].images[0].id")
        .value(contains(primary.getId().toString())));
  }

  @Test void shouldServeActiveImageWithoutLoginAndRequireCacheRevalidation() throws Exception {
    mvc.perform(get(URL, "forest-dragon", primary.getId()))
      .andExpect(status().isOk()).andExpect(content().bytes(bytes))
      .andExpect(content().contentType("image/png"))
      .andExpect(header().string("X-Content-Type-Options", "nosniff"))
      .andExpect(header().string("Cache-Control", "no-cache"))
      .andExpect(header().exists("ETag"));
  }

  @Test void shouldReturnNotModifiedForUnchangedImage() throws Exception {
    var result = mvc.perform(get(URL, "forest-dragon", primary.getId())).andReturn();
    mvc.perform(get(URL, "forest-dragon", primary.getId()).header("If-None-Match", result.getResponse().getHeader("ETag")))
      .andExpect(status().isNotModified());
  }

  @Test void shouldHideImageEvenWhenBrowserHasAnExistingEtag() throws Exception {
    var result = mvc.perform(get(URL, "forest-dragon", primary.getId())).andReturn();
    jdbc.update("UPDATE products SET active = false WHERE id = 'pattern-001'");
    clearInvocations(storage);
    mvc.perform(get(URL, "forest-dragon", primary.getId()).header("If-None-Match", result.getResponse().getHeader("ETag")))
      .andExpect(status().isNotFound());
    mvc.perform(get("/api/products/forest-dragon")).andExpect(status().isNotFound());
    verifyNoInteractions(storage);
  }

  @Test void shouldRejectImageBelongingToAnotherProduct() throws Exception {
    mvc.perform(get(URL, "floral-monogram", primary.getId())).andExpect(status().isNotFound());
    verifyNoInteractions(storage);
  }

  @Test void shouldRejectMissingProductOrImage() throws Exception {
    mvc.perform(get(URL, "missing-product", primary.getId())).andExpect(status().isNotFound());
    mvc.perform(get(URL, "forest-dragon", UUID.randomUUID())).andExpect(status().isNotFound());
    verifyNoInteractions(storage);
  }

  @Test void shouldReturnNotFoundForMissingStoredFile() throws Exception {
    when(storage.load(primary.getStorageKey())).thenThrow(new NoSuchFileException("missing"));
    mvc.perform(get(URL, "forest-dragon", primary.getId())).andExpect(status().isNotFound());
  }

  @Test void shouldReturnAnEmptyGalleryForProductWithoutImages() throws Exception {
    mvc.perform(get("/api/products/floral-monogram")).andExpect(status().isOk())
      .andExpect(jsonPath("$.images").isEmpty());
  }

  @Test void shouldInvalidateTheImageEtagAfterProductChanges() throws Exception {
    var result = mvc.perform(get(URL, "forest-dragon", primary.getId())).andReturn();
    jdbc.update("UPDATE products SET version = version + 1 WHERE id = 'pattern-001'" );
    entityManager.clear();
    mvc.perform(get(URL, "forest-dragon", primary.getId()).header("If-None-Match", result.getResponse().getHeader("ETag")))
      .andExpect(status().isOk()).andExpect(content().bytes(bytes));
  }

  @Test void shouldNotExposeImagesOfGarmentWithoutAvailableVariants() throws Exception {
    var id = UUID.randomUUID();
    images.saveAndFlush(new ProductImage(id, "embroidered-002", id + ".png", "image/png", 4, 2, 2, 0, true, "", ""));
    jdbc.update("UPDATE garment_variants SET active = false WHERE product_id = 'embroidered-002'");
    mvc.perform(get(URL, "embroidered-sweatshirt", id)).andExpect(status().isNotFound());
    verifyNoInteractions(storage);
  }

  private ProductImage image(int position, boolean main, String pl, String en) {
    var id = UUID.randomUUID();
    return images.saveAndFlush(new ProductImage(id, "pattern-001", id + ".png", "image/png", 4, 2, 2, position, main, pl, en));
  }
}
