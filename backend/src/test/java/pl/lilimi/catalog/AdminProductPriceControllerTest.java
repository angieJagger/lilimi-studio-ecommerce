package pl.lilimi.catalog;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

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
class AdminProductPriceControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private DigitalProductPriceRepository prices;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSavePriceAndIncreaseProductVersion() throws Exception {
    long version = currentVersion();

    mockMvc.perform(
        patch("/api/admin/products/pattern-001/digital-price")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(3500, version))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.digitalPriceInGrosz").value(3500))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(version + 1)
      ));

    entityManager.clear();

    assertThat(prices.findById("pattern-001").orElseThrow()
      .getPriceInGrosz()).isEqualTo(3500);

    assertThat(currentVersion()).isEqualTo(version + 1);
  }

  @Test
  void shouldRejectStaleVersionWithoutChangingPrice() throws Exception {
    long version = currentVersion();
    int originalPrice = currentPrice();

    mockMvc.perform(
        patch("/api/admin/products/pattern-001/digital-price")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(3500, version + 1))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    entityManager.clear();

    assertThat(currentPrice()).isEqualTo(originalPrice);
    assertThat(currentVersion()).isEqualTo(version);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"priceInGrosz\":0,\"expectedVersion\":0}",
    "{\"priceInGrosz\":-100,\"expectedVersion\":0}",
    "{\"expectedVersion\":0}",
    "{\"priceInGrosz\":3500}",
    "{\"priceInGrosz\":3500,\"expectedVersion\":-1}"
  })
  void shouldRejectInvalidRequestWithoutChangingPrice(String body)
    throws Exception {
    int originalPrice = currentPrice();
    long originalVersion = currentVersion();

    mockMvc.perform(
        patch("/api/admin/products/pattern-001/digital-price")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isBadRequest());

    entityManager.clear();

    assertThat(currentPrice()).isEqualTo(originalPrice);
    assertThat(currentVersion()).isEqualTo(originalVersion);
  }

  @Test
  void shouldRejectGarmentProduct() throws Exception {
    var garment = products.findById("embroidered-002")
      .orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/embroidered-002/digital-price")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(3500, garment.getVersion()))
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnNotFoundForMissingProduct() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/missing-product/digital-price")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(3500, 0))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/pattern-001/digital-price")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(3500, currentVersion()))
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/pattern-001/digital-price")
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(3500, currentVersion()))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectRequestWithoutCsrf() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/pattern-001/digital-price")
          .with(user("admin@example.com").roles("ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(3500, currentVersion()))
      )
      .andExpect(status().isForbidden());
  }

  private long currentVersion() {
    return products.findById("pattern-001").orElseThrow()
      .getVersion();
  }

  private int currentPrice() {
    return prices.findById("pattern-001").orElseThrow()
      .getPriceInGrosz();
  }

  private String requestBody(int priceInGrosz, long version) {
    return """
      {
        "priceInGrosz": %d,
        "expectedVersion": %d
      }
      """.formatted(priceInGrosz, version);
  }
}
