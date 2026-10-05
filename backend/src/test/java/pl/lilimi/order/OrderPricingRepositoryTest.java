package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.catalog.ProductRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderPricingRepositoryTest {

  @Autowired
  private ProductRepository productRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldReturnPriceForExactEmbroideryOption() {
    assertThat(productRepository.findActiveSweatshirtPrice(
      "embroidered-002",
      "pattern-001",
      "men",
      "M",
      "black",
      "small-front"
    )).contains(14900);

    assertThat(productRepository.findActiveSweatshirtPrice(
      "embroidered-002",
      "pattern-001",
      "men",
      "M",
      "black",
      "large-back"
    )).contains(16900);
  }

  @Test
  void shouldRejectNonexistentConfiguration() {
    assertThat(productRepository.findActiveSweatshirtPrice(
      "embroidered-002",
      "pattern-001",
      "men",
      "92",
      "black",
      "small-front"
    )).isEmpty();
  }

  @Test
  void shouldRejectInactiveVariant() {
    int updated = jdbcTemplate.update("""
            UPDATE garment_variants
            SET active = FALSE
            WHERE product_id = 'embroidered-002'
              AND pattern_id = 'pattern-001'
              AND fit = 'men'
              AND size = 'M'
              AND color = 'black'
              AND embroidery_option_id = 'small-front'
            """);

    assertThat(updated).isEqualTo(1);
    assertThat(findSelectedPrice()).isEmpty();
  }

  @Test
  void shouldRejectInactiveProduct() {
    jdbcTemplate.update("""
            UPDATE products
            SET active = FALSE
            WHERE id = 'embroidered-002'
            """);

    assertThat(findSelectedPrice()).isEmpty();
  }

  @Test
  void shouldRejectInactivePattern() {
    jdbcTemplate.update("""
            UPDATE products
            SET active = FALSE
            WHERE id = 'pattern-001'
            """);

    assertThat(findSelectedPrice()).isEmpty();
  }

  private java.util.Optional<Integer> findSelectedPrice() {
    return productRepository.findActiveSweatshirtPrice(
      "embroidered-002",
      "pattern-001",
      "men",
      "M",
      "black",
      "small-front"
    );
  }
}
