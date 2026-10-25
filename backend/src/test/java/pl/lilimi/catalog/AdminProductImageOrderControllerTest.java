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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
class AdminProductImageOrderControllerTest {

  private static final String PRODUCT_ID = "pattern-001";
  private static final String URL =
    "/api/admin/products/{productId}/images/order";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private ProductImageRepository images;

  private ProductImage first;
  private ProductImage second;
  private ProductImage third;

  @BeforeEach
  void setUp() {
    first = saveImage(PRODUCT_ID, 0, true);
    second = saveImage(PRODUCT_ID, 1, false);
    third = saveImage(PRODUCT_ID, 2, false);
  }

  @Test
  void shouldReorderImagesWithoutChangingPrimaryImage()
    throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        patch(URL, PRODUCT_ID)
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(
            List.of(third.getId(), first.getId(), second.getId()),
            version
          ))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(version + 1)
      ))
      .andExpect(jsonPath("$.images[0].id").value(
        third.getId().toString()
      ))
      .andExpect(jsonPath("$.images[1].id").value(
        first.getId().toString()
      ))
      .andExpect(jsonPath("$.images[2].id").value(
        second.getId().toString()
      ));

    var saved = images
      .findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID);

    assertThat(saved)
      .extracting(ProductImage::getId)
      .containsExactly(third.getId(), first.getId(), second.getId());

    assertThat(saved)
      .extracting(ProductImage::getSortOrder)
      .containsExactly(0, 1, 2);

    assertThat(saved)
      .filteredOn(ProductImage::isPrimaryImage)
      .extracting(ProductImage::getId)
      .containsExactly(first.getId());
  }

  @Test
  void shouldRejectDuplicateIdentifiers() throws Exception {
    assertInvalidOrder(
      List.of(first.getId(), first.getId(), third.getId())
    );
  }

  @Test
  void shouldRejectMissingIdentifier() throws Exception {
    assertInvalidOrder(List.of(first.getId(), second.getId()));
  }

  @Test
  void shouldRejectImageFromAnotherProduct() throws Exception {
    var foreign = saveImage("pattern-002", 0, true);

    assertInvalidOrder(
      List.of(first.getId(), second.getId(), foreign.getId())
    );
  }

  @Test
  void shouldRejectEmptyList() throws Exception {
    assertInvalidOrder(List.of());
  }

  @Test
  void shouldRejectStaleVersion() throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        patch(URL, PRODUCT_ID)
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(
            List.of(third.getId(), second.getId(), first.getId()),
            version + 1
          ))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    assertOriginalGallery();
    assertThat(currentVersion()).isEqualTo(version);
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        patch(URL, PRODUCT_ID)
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(validRequestBody())
      )
      .andExpect(status().isUnauthorized());

    assertOriginalGallery();
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        patch(URL, PRODUCT_ID)
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(validRequestBody())
      )
      .andExpect(status().isForbidden());

    assertOriginalGallery();
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    mockMvc.perform(
        patch(URL, PRODUCT_ID)
          .with(user("admin@example.com").roles("ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(validRequestBody())
      )
      .andExpect(status().isForbidden());

    assertOriginalGallery();
  }

  private void assertInvalidOrder(List<UUID> ids) throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        patch(URL, PRODUCT_ID)
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(ids, version))
      )
      .andExpect(status().isBadRequest());

    assertOriginalGallery();
    assertThat(currentVersion()).isEqualTo(version);
  }

  private void assertOriginalGallery() {
    var saved = images
      .findAllByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID);

    assertThat(saved)
      .extracting(ProductImage::getId)
      .containsExactly(first.getId(), second.getId(), third.getId());

    assertThat(saved)
      .extracting(ProductImage::getSortOrder)
      .containsExactly(0, 1, 2);

    assertThat(saved)
      .filteredOn(ProductImage::isPrimaryImage)
      .extracting(ProductImage::getId)
      .containsExactly(first.getId());
  }

  private ProductImage saveImage(
    String productId,
    int position,
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
      position,
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

  private String validRequestBody() {
    return requestBody(
      List.of(third.getId(), second.getId(), first.getId()),
      currentVersion()
    );
  }

  private String requestBody(List<UUID> ids, long version) {
    String identifiers = ids.stream()
      .map(id -> "\"" + id + "\"")
      .collect(Collectors.joining(","));

    return """
      {
        "imageIds": [%s],
        "expectedVersion": %d
      }
      """.formatted(identifiers, version);
  }
}
