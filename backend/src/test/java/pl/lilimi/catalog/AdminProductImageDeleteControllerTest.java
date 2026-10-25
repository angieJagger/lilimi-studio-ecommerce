package pl.lilimi.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.catalog.media.ProductImageStorage;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductImageDeleteControllerTest {

  private static final String PRODUCT_ID = "pattern-001";
  private static final String URL =
    "/api/admin/products/{productId}/images/{imageId}";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private ProductImageRepository images;

  @MockitoBean
  private ProductImageStorage storage;

  private ProductImage first;
  private ProductImage second;

  @BeforeEach
  void setUp() {
    clearInvocations(storage);
    first = saveImage(PRODUCT_ID, 0, true);
    second = saveImage(PRODUCT_ID, 1, false);
  }

  @Test
  void shouldDeletePrimaryImageAndPromoteNextImage() throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        delete(URL, PRODUCT_ID, first.getId())
          .param("expectedVersion", String.valueOf(version))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(version + 1)
      ))
      .andExpect(jsonPath("$.images.length()").value(1))
      .andExpect(jsonPath("$.images[0].id").value(
        second.getId().toString()
      ))
      .andExpect(jsonPath("$.images[0].primaryImage").value(true));

    assertThat(images.findById(first.getId())).isEmpty();

    // The test transaction has not committed yet.
    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldDeleteSecondaryImageWithoutChangingPrimary()
    throws Exception {
    mockMvc.perform(
        delete(URL, PRODUCT_ID, second.getId())
          .param("expectedVersion", String.valueOf(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.images.length()").value(1))
      .andExpect(jsonPath("$.images[0].id").value(
        first.getId().toString()
      ))
      .andExpect(jsonPath("$.images[0].primaryImage").value(true));

    assertThat(images.findById(second.getId())).isEmpty();
    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldAllowDeletingLastImage() throws Exception {
    images.delete(second);
    images.flush();

    mockMvc.perform(
        delete(URL, PRODUCT_ID, first.getId())
          .param("expectedVersion", String.valueOf(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.images").isEmpty());

    assertThat(
      images.findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)
    ).isEmpty();
  }

  @Test
  void shouldRejectStaleVersionWithoutDeletingImage()
    throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        delete(URL, PRODUCT_ID, first.getId())
          .param("expectedVersion", String.valueOf(version + 1))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    assertOriginalGallery();
    assertThat(currentVersion()).isEqualTo(version);
    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldRejectImageBelongingToAnotherProduct() throws Exception {
    var foreign = saveImage("pattern-002", 0, true);

    mockMvc.perform(
        delete(URL, PRODUCT_ID, foreign.getId())
          .param("expectedVersion", String.valueOf(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNotFound());

    assertOriginalGallery();
    assertThat(images.findById(foreign.getId())).isPresent();
    verify(storage, never()).delete(anyString());
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        delete(URL, PRODUCT_ID, first.getId())
          .param("expectedVersion", String.valueOf(currentVersion()))
          .with(csrf())
      )
      .andExpect(status().isUnauthorized());

    assertOriginalGallery();
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        delete(URL, PRODUCT_ID, first.getId())
          .param("expectedVersion", String.valueOf(currentVersion()))
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
      )
      .andExpect(status().isForbidden());

    assertOriginalGallery();
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    mockMvc.perform(
        delete(URL, PRODUCT_ID, first.getId())
          .param("expectedVersion", String.valueOf(currentVersion()))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isForbidden());

    assertOriginalGallery();
  }

  @Test
  void shouldPreserveExistingImageAndFileWhenTransactionRollsBack()
    throws Exception {
    // Commit only the fixture so that rollback can restore its deletion.
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();

    try {
      mockMvc.perform(
          delete(URL, PRODUCT_ID, first.getId())
            .param("expectedVersion", String.valueOf(currentVersion()))
            .with(user("admin@example.com").roles("ADMIN"))
            .with(csrf())
        )
        .andExpect(status().isOk());

      TestTransaction.flagForRollback();
      TestTransaction.end();
      TestTransaction.start();

      assertOriginalGallery();
      verify(storage, never()).delete(anyString());
    } finally {
      // Remove the committed fixture without deleting any real files.
      if (!TestTransaction.isActive()) {
        TestTransaction.start();
      }

      images.deleteAllById(
        java.util.List.of(first.getId(), second.getId())
      );
      images.flush();

      TestTransaction.flagForCommit();
      TestTransaction.end();
    }
  }

  private ProductImage saveImage(
    String productId,
    int sortOrder,
    boolean primary
  ) {
    UUID id = UUID.randomUUID();

    return images.saveAndFlush(new ProductImage(
      id,
      productId,
      id + ".png",
      "image/png",
      4,
      2,
      2,
      sortOrder,
      primary,
      "",
      ""
    ));
  }

  private long currentVersion() {
    return products.findById(PRODUCT_ID)
      .orElseThrow()
      .getVersion();
  }

  private void assertOriginalGallery() {
    var saved = images
      .findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID);

    assertThat(saved)
      .extracting(ProductImage::getId)
      .containsExactly(first.getId(), second.getId());

    assertThat(saved)
      .filteredOn(ProductImage::isPrimaryImage)
      .extracting(ProductImage::getId)
      .containsExactly(first.getId());
  }
}
