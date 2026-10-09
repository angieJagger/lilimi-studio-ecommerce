package pl.lilimi.catalog;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductVisibilityControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldHideProductAndRemovePublicAccess() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();
    long originalVersion = product.getVersion();

    assertThat(product.isActive()).isTrue();

    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(false, originalVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(originalVersion + 1)
      ));

    entityManager.clear();

    var saved = products.findById("pattern-001").orElseThrow();

    assertThat(saved.isActive()).isFalse();
    assertThat(saved.getVersion()).isEqualTo(originalVersion + 1);

    mockMvc.perform(
        get("/api/products/forest-dragon")
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldMakeHiddenProductVisibleAgain() throws Exception {
    jdbc.update(
      "UPDATE products SET active = FALSE WHERE id = ?",
      "pattern-001"
    );
    entityManager.clear();

    var product = products.findById("pattern-001").orElseThrow();
    long originalVersion = product.getVersion();

    assertThat(product.isActive()).isFalse();

    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(true, originalVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(originalVersion + 1)
      ));

    entityManager.clear();

    var saved = products.findById("pattern-001").orElseThrow();

    assertThat(saved.isActive()).isTrue();
    assertThat(saved.getVersion()).isEqualTo(originalVersion + 1);

    mockMvc.perform(
        get("/api/products/forest-dragon")
      )
      .andExpect(status().isOk());
  }

  @Test
  void shouldRejectIncorrectVersionWithoutChangingVisibility()
    throws Exception {
    var product = products.findById("pattern-001").orElseThrow();
    long originalVersion = product.getVersion();

    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(false, originalVersion + 1))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    entityManager.clear();

    var saved = products.findById("pattern-001").orElseThrow();

    assertThat(saved.isActive()).isTrue();
    assertThat(saved.getVersion()).isEqualTo(originalVersion);
  }

  @Test
  void shouldReturnNotFoundForMissingProduct() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", "missing-product")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(false, 0))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", product.getId())
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(false, product.getVersion()))
      )
      .andExpect(status().isUnauthorized());

    assertProductStillVisible();
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", product.getId())
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(false, product.getVersion()))
      )
      .andExpect(status().isForbidden());

    assertProductStillVisible();
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(false, product.getVersion()))
      )
      .andExpect(status().isForbidden());

    assertProductStillVisible();
  }

  @Test
  void shouldRejectMissingVisibility() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "expectedVersion": %d
            }
            """.formatted(product.getVersion()))
      )
      .andExpect(status().isBadRequest());

    assertProductStillVisible();
  }

  @Test
  void shouldRejectMissingVersion() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", "pattern-001")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "active": false
            }
            """)
      )
      .andExpect(status().isBadRequest());

    assertProductStillVisible();
  }

  @Test
  void shouldRejectNegativeVersion() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/{id}/visibility", "pattern-001")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(false, -1))
      )
      .andExpect(status().isBadRequest());

    assertProductStillVisible();
  }

  private void assertProductStillVisible() {
    entityManager.clear();

    var product = products.findById("pattern-001").orElseThrow();

    assertThat(product.isActive()).isTrue();
  }

  private static String requestBody(
    boolean active,
    long expectedVersion
  ) {
    return """
      {
        "active": %s,
        "expectedVersion": %d
      }
      """.formatted(active, expectedVersion);
  }
}
