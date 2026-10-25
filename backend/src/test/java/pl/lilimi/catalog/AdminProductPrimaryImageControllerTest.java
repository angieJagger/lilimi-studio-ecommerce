package pl.lilimi.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductPrimaryImageControllerTest {

  private static final String PRODUCT_ID = "pattern-001";
  private static final String URL =
    "/api/admin/products/{productId}/images/{imageId}/primary";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private ProductImageRepository images;

  private ProductImage firstImage;
  private ProductImage secondImage;

  @BeforeEach
  void setUp() {
    firstImage = saveImage(PRODUCT_ID, 0, true);
    secondImage = saveImage(PRODUCT_ID, 1, false);
  }

  @Test
  void shouldChangePrimaryImageAndIncreaseVersion() throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        patch(URL, PRODUCT_ID, secondImage.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(version))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(version + 1)
      ))
      .andExpect(jsonPath("$.images[0].primaryImage").value(false))
      .andExpect(jsonPath("$.images[1].primaryImage").value(true));

    var saved = images
      .findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID);

    assertThat(saved)
      .filteredOn(ProductImage::isPrimaryImage)
      .extracting(ProductImage::getId)
      .containsExactly(secondImage.getId());

    assertThat(saved)
      .extracting(ProductImage::getSortOrder)
      .containsExactly(0, 1);

    assertThat(currentVersion()).isEqualTo(version + 1);
  }

  @Test
  void shouldRejectStaleVersionWithoutChangingPrimaryImage()
    throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        patch(URL, PRODUCT_ID, secondImage.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(version + 1))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    assertOriginalPrimaryImage();
    assertThat(currentVersion()).isEqualTo(version);
  }

  @Test
  void shouldRejectImageBelongingToAnotherProduct() throws Exception {
    var foreignImage = saveImage("pattern-002", 0, true);
    long version = currentVersion();

    mockMvc.perform(
        patch(URL, PRODUCT_ID, foreignImage.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(version))
      )
      .andExpect(status().isNotFound());

    assertOriginalPrimaryImage();
    assertThat(foreignImage.isPrimaryImage()).isTrue();
    assertThat(currentVersion()).isEqualTo(version);
  }

  @Test
  void shouldReturnNotFoundForMissingImage() throws Exception {
    mockMvc.perform(
        patch(URL, PRODUCT_ID, UUID.randomUUID())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(currentVersion()))
      )
      .andExpect(status().isNotFound());

    assertOriginalPrimaryImage();
  }

  @Test
  void shouldReturnNotFoundForMissingProduct() throws Exception {
    mockMvc.perform(
        patch(URL, "missing-product", secondImage.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(0))
      )
      .andExpect(status().isNotFound());

    assertOriginalPrimaryImage();
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        patch(URL, PRODUCT_ID, secondImage.getId())
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(currentVersion()))
      )
      .andExpect(status().isUnauthorized());

    assertOriginalPrimaryImage();
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        patch(URL, PRODUCT_ID, secondImage.getId())
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(currentVersion()))
      )
      .andExpect(status().isForbidden());

    assertOriginalPrimaryImage();
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    mockMvc.perform(
        patch(URL, PRODUCT_ID, secondImage.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(currentVersion()))
      )
      .andExpect(status().isForbidden());

    assertOriginalPrimaryImage();
  }

  private ProductImage saveImage(
    String productId,
    int sortOrder,
    boolean primaryImage
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
      primaryImage,
      "",
      ""
    ));
  }

  private long currentVersion() {
    return products.findById(PRODUCT_ID)
      .orElseThrow()
      .getVersion();
  }

  private String requestBody(long version) {
    return """
      {"expectedVersion": %d}
      """.formatted(version);
  }

  private void assertOriginalPrimaryImage() {
    assertThat(
      images.findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)
    )
      .filteredOn(ProductImage::isPrimaryImage)
      .extracting(ProductImage::getId)
      .containsExactly(firstImage.getId());
  }
}
