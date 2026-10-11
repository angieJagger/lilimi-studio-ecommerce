package pl.lilimi.catalog;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminGarmentVariantUpdateControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private GarmentVariantRepository variants;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSavePriceAndVisibilityAndIncreaseVersion()
    throws Exception {
    var variant = firstVariant();
    UUID id = variant.getId();
    long originalVersion = variant.getVersion();

    mockMvc.perform(
        patch(
          "/api/admin/products/embroidered-002/variants/{id}",
          id
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(18900, false, originalVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(id.toString()))
      .andExpect(jsonPath("$.priceInGrosz").value(18900))
      .andExpect(jsonPath("$.active").value(false))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(originalVersion + 1)
      ));

    entityManager.clear();

    var saved = variants.findById(id).orElseThrow();

    assertThat(saved.getPriceInGrosz()).isEqualTo(18900);
    assertThat(saved.isActive()).isFalse();
    assertThat(saved.getVersion()).isEqualTo(originalVersion + 1);
  }

  @Test
  void shouldRejectStaleVersionWithoutChangingVariant()
    throws Exception {
    var variant = firstVariant();
    UUID id = variant.getId();
    int originalPrice = variant.getPriceInGrosz();
    boolean originalActive = variant.isActive();
    long originalVersion = variant.getVersion();

    mockMvc.perform(
        patch(
          "/api/admin/products/embroidered-002/variants/{id}",
          id
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(
            18900,
            !originalActive,
            originalVersion + 1
          ))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "GARMENT_VARIANT_VERSION_CONFLICT"
      ));

    entityManager.clear();

    var unchanged = variants.findById(id).orElseThrow();

    assertThat(unchanged.getPriceInGrosz()).isEqualTo(originalPrice);
    assertThat(unchanged.isActive()).isEqualTo(originalActive);
    assertThat(unchanged.getVersion()).isEqualTo(originalVersion);
  }

  @Test
  void shouldReturnNotFoundForMissingVariant() throws Exception {
    mockMvc.perform(
        patch(
          "/api/admin/products/embroidered-002/variants/{id}",
          UUID.randomUUID()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(18900, true, 0))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnNotFoundForMissingProduct() throws Exception {
    var variant = firstVariant();

    mockMvc.perform(
        patch(
          "/api/admin/products/missing-product/variants/{id}",
          variant.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(
            18900,
            false,
            variant.getVersion()
          ))
      )
      .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"priceInGrosz\":0,\"active\":true,\"expectedVersion\":0}",
    "{\"priceInGrosz\":-100,\"active\":true,\"expectedVersion\":0}",
    "{\"active\":true,\"expectedVersion\":0}",
    "{\"priceInGrosz\":18900,\"expectedVersion\":0}",
    "{\"priceInGrosz\":18900,\"active\":true}",
    "{\"priceInGrosz\":18900,\"active\":true,\"expectedVersion\":-1}"
  })
  void shouldRejectInvalidRequestWithoutChangingVariant(String body)
    throws Exception {
    var variant = firstVariant();
    UUID id = variant.getId();
    int originalPrice = variant.getPriceInGrosz();
    boolean originalActive = variant.isActive();
    long originalVersion = variant.getVersion();

    mockMvc.perform(
        patch(
          "/api/admin/products/embroidered-002/variants/{id}",
          id
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isBadRequest());

    entityManager.clear();

    var unchanged = variants.findById(id).orElseThrow();

    assertThat(unchanged.getPriceInGrosz()).isEqualTo(originalPrice);
    assertThat(unchanged.isActive()).isEqualTo(originalActive);
    assertThat(unchanged.getVersion()).isEqualTo(originalVersion);
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    var variant = firstVariant();

    mockMvc.perform(
        patch(
          "/api/admin/products/embroidered-002/variants/{id}",
          variant.getId()
        )
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(18900, false, variant.getVersion()))
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    var variant = firstVariant();

    mockMvc.perform(
        patch(
          "/api/admin/products/embroidered-002/variants/{id}",
          variant.getId()
        )
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(18900, false, variant.getVersion()))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectRequestWithoutCsrf() throws Exception {
    var variant = firstVariant();

    mockMvc.perform(
        patch(
          "/api/admin/products/embroidered-002/variants/{id}",
          variant.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(18900, false, variant.getVersion()))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectVariantBelongingToAnotherProduct()
    throws Exception {
    entityManager.createNativeQuery("""
      INSERT INTO products (
        id,
        slug,
        product_type,
        active,
        made_to_order,
        personalization_available
      )
      SELECT
        'test-other-sweatshirt',
        'test-other-sweatshirt',
        product_type,
        active,
        made_to_order,
        personalization_available
      FROM products
      WHERE id = 'embroidered-002'
      """).executeUpdate();

    var variant = firstVariant();
    UUID id = variant.getId();
    int originalPrice = variant.getPriceInGrosz();
    boolean originalActive = variant.isActive();
    long originalVersion = variant.getVersion();

    mockMvc.perform(
        patch(
          "/api/admin/products/test-other-sweatshirt/variants/{id}",
          id
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(
            18900,
            !originalActive,
            originalVersion
          ))
      )
      .andExpect(status().isNotFound());

    entityManager.clear();

    var unchanged = variants.findById(id).orElseThrow();

    assertThat(unchanged.getProductId()).isEqualTo("embroidered-002");
    assertThat(unchanged.getPriceInGrosz()).isEqualTo(originalPrice);
    assertThat(unchanged.isActive()).isEqualTo(originalActive);
    assertThat(unchanged.getVersion()).isEqualTo(originalVersion);
  }

  private GarmentVariant firstVariant() {
    return variants.findAllByProductId(
      "embroidered-002",
      PageRequest.of(0, 1)
    ).getContent().getFirst();
  }

  private String requestBody(
    int priceInGrosz,
    boolean active,
    long expectedVersion
  ) {
    return """
      {
        "priceInGrosz": %d,
        "active": %s,
        "expectedVersion": %d
      }
      """.formatted(priceInGrosz, active, expectedVersion);
  }
}
