package pl.lilimi.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class ProductServiceTest {

  @Autowired
  private ProductService productService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldReturnDigitalProductWithTranslationsAndFixedPrice() {
    ProductResponse dragon = findProduct("pattern-001");

    assertThat(dragon.name())
      .containsEntry("pl", "Wzór haftu „Leśny smok”")
      .containsEntry("en", "Forest Dragon embroidery pattern");

    assertThat(dragon.priceInGrosz()).isEqualTo(2900);
    assertThat(dragon.priceType()).isEqualTo("fixed");
    assertThat(dragon.currency()).isEqualTo("PLN");
  }

  @Test
  void shouldCalculateStartingPriceFromActiveVariants() {
    assertThat(findProduct("embroidered-002").priceInGrosz())
      .isEqualTo(12900);

    jdbcTemplate.update("""
                UPDATE garment_variants
                SET active = FALSE
                WHERE product_id = ?
                  AND fit = 'children'
                  AND embroidery_option_id = 'small-front'
                """, "embroidered-002");

    ProductResponse sweatshirt = findProduct("embroidered-002");

    assertThat(sweatshirt.priceInGrosz()).isEqualTo(14900);
    assertThat(sweatshirt.priceType()).isEqualTo("from");
  }

  @Test
  void shouldHideGarmentWithoutActiveVariants() {
    jdbcTemplate.update("""
                UPDATE garment_variants
                SET active = FALSE
                WHERE product_id = ?
                """, "embroidered-002");

    assertThat(productService.getActiveProducts())
      .extracting(ProductResponse::id)
      .doesNotContain("embroidered-002")
      .contains("pattern-001");
  }

  private ProductResponse findProduct(String id) {
    return productService.getActiveProducts()
      .stream()
      .filter(product -> product.id().equals(id))
      .findFirst()
      .orElseThrow(() ->
        new AssertionError("Product not found: " + id)
      );
  }
}
