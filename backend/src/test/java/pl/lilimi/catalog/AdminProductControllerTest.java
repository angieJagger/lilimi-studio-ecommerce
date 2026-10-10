package pl.lilimi.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        get("/api/admin/products")
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        get("/api/admin/products")
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowAdministratorWithDefaultPagination()
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.items").isArray())
      .andExpect(jsonPath("$.totalElements").value(
        Math.toIntExact(products.count())
      ));
  }

  @Test
  void shouldIncludeInactiveProducts() throws Exception {
    jdbc.update(
      "UPDATE products SET active = FALSE WHERE id = ?",
      "pattern-001"
    );

    mockMvc.perform(
        get("/api/admin/products")
          .param("size", "100")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath(
        "$.items[?(@.id == 'pattern-001')].active"
      ).value(hasItem(false)))
      .andExpect(jsonPath(
        "$.items[?(@.id == 'pattern-001')].productType"
      ).value(hasItem("digital")));
  }

  @Test
  void shouldReturnRequestedPageAndSize() throws Exception {
    long total = products.count();

    mockMvc.perform(
        get("/api/admin/products")
          .param("page", "1")
          .param("size", "1")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(1))
      .andExpect(jsonPath("$.size").value(1))
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.totalElements").value(
        Math.toIntExact(total)
      ))
      .andExpect(jsonPath("$.totalPages").value(
        Math.toIntExact(total)
      ));
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 20",
    "0, 0",
    "0, 101"
  })
  void shouldRejectInvalidPagination(int page, int size)
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products")
          .param("page", Integer.toString(page))
          .param("size", Integer.toString(size))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isBadRequest());
  }
  @Test
  void shouldReturnProductDetailsWithBothTranslations()
    throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        get("/api/admin/products/{id}", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("pattern-001"))
      .andExpect(jsonPath("$.slug").value("forest-dragon"))
      .andExpect(jsonPath("$.productType").value("digital"))
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(product.getVersion())
      ))
      .andExpect(jsonPath("$.translations.length()").value(2))
      .andExpect(jsonPath("$.translations[0].language").value("en"))
      .andExpect(jsonPath("$.translations[1].language").value("pl"))
      .andExpect(jsonPath("$.translations[0].name").isNotEmpty())
      .andExpect(jsonPath("$.translations[0].description").isNotEmpty())
      .andExpect(jsonPath("$.translations[1].name").isNotEmpty())
      .andExpect(jsonPath("$.translations[1].description").isNotEmpty());
  }

  @Test
  void shouldReturnDetailsOfHiddenProduct() throws Exception {
    jdbc.update(
      "UPDATE products SET active = FALSE WHERE id = ?",
      "pattern-001"
    );

    mockMvc.perform(
        get("/api/admin/products/{id}", "pattern-001")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false))
      .andExpect(jsonPath("$.translations.length()").value(2));
  }

  @Test
  void shouldRejectAnonymousUserForProductDetails()
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products/{id}", "pattern-001")
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomerForProductDetails() throws Exception {
    mockMvc.perform(
        get("/api/admin/products/{id}", "pattern-001")
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnNotFoundForMissingProductDetails()
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products/{id}", "missing-product")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isNotFound());
  }
}
