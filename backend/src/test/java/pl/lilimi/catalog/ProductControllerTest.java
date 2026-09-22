package pl.lilimi.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class ProductControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldReturnDigitalProductBySlug() throws Exception {
    mockMvc.perform(get("/api/products/forest-dragon"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("pattern-001"))
      .andExpect(jsonPath("$.slug").value("forest-dragon"))
      .andExpect(jsonPath("$.name.pl")
        .value("Wzór haftu „Leśny smok”"))
      .andExpect(jsonPath("$.priceInGrosz").value(2900))
      .andExpect(jsonPath("$.priceType").value("fixed"))
      .andExpect(jsonPath("$.currency").value("PLN"));
  }

  @Test
  void shouldReturnSweatshirtWithStartingPrice() throws Exception {
    mockMvc.perform(get("/api/products/embroidered-sweatshirt"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("embroidered-002"))
      .andExpect(jsonPath("$.priceInGrosz").value(12900))
      .andExpect(jsonPath("$.priceType").value("from"));
  }

  @Test
  void shouldReturnNotFoundForUnknownSlug() throws Exception {
    mockMvc.perform(get("/api/products/unknown-product"))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnNotFoundForInactiveProduct() throws Exception {
    jdbcTemplate.update("""
                UPDATE products
                SET active = FALSE
                WHERE id = ?
                """, "pattern-001");

    mockMvc.perform(get("/api/products/forest-dragon"))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnNotFoundForGarmentWithoutActiveVariants()
    throws Exception {
    jdbcTemplate.update("""
                UPDATE garment_variants
                SET active = FALSE
                WHERE product_id = ?
                """, "embroidered-002");

    mockMvc.perform(get("/api/products/embroidered-sweatshirt"))
      .andExpect(status().isNotFound());
  }
}
