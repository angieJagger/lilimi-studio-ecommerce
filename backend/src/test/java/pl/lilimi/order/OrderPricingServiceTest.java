package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.api.OrderItemRequest;
import pl.lilimi.order.api.SweatshirtConfigurationRequest;
import pl.lilimi.order.application.OrderPricingService;
import pl.lilimi.order.application.OrderProductUnavailableException;
import pl.lilimi.order.application.PricedOrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderPricingServiceTest {

  @Autowired
  private OrderPricingService pricingService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void shouldPriceMixedCart() {
    var pricedItems = pricingService.priceItems(
      List.of(
        new OrderItemRequest.Digital("pattern-001", 1),
        sweatshirt()
      ),
      "en"
    );

    assertThat(pricedItems).hasSize(2);

    var digital = pricedItems.getFirst();

    assertThat(digital.productName())
      .isEqualTo("Forest Dragon embroidery pattern");
    assertThat(digital.patternName()).isNull();
    assertThat(digital.unitPriceInGrosz()).isEqualTo(2900);
    assertThat(digital.lineTotalInGrosz()).isEqualTo(2900);

    var garment = pricedItems.get(1);

    assertThat(garment.productName())
      .isEqualTo("Embroidered sweatshirt");
    assertThat(garment.patternName())
      .isEqualTo("Forest Dragon embroidery pattern");
    assertThat(garment.quantity()).isEqualTo(2);
    assertThat(garment.unitPriceInGrosz()).isEqualTo(14900);
    assertThat(garment.lineTotalInGrosz()).isEqualTo(29800);

    long subtotal = pricedItems.stream()
      .mapToLong(PricedOrderItem::lineTotalInGrosz)
      .sum();

    assertThat(subtotal).isEqualTo(32700);
  }

  @Test
  void shouldUseCurrentPriceFromDatabase() {
    int updated = jdbcTemplate.update("""
            UPDATE garment_variants
            SET price_in_grosz = 17900
            WHERE product_id = 'embroidered-002'
              AND pattern_id = 'pattern-001'
              AND fit = 'men'
              AND size = 'M'
              AND color = 'black'
              AND embroidery_option_id = 'small-front'
            """);

    assertThat(updated).isEqualTo(1);

    var pricedItem = pricingService.priceItems(
      List.of(sweatshirt()),
      "pl"
    ).getFirst();

    assertThat(pricedItem.unitPriceInGrosz()).isEqualTo(17900);
    assertThat(pricedItem.lineTotalInGrosz()).isEqualTo(35800);
  }

  @Test
  void shouldRejectInactiveProductInMixedCart() {
    jdbcTemplate.update("""
            UPDATE products
            SET active = FALSE
            WHERE id = 'embroidered-002'
            """);

    assertThatThrownBy(() -> pricingService.priceItems(
      List.of(
        new OrderItemRequest.Digital("pattern-001", 1),
        sweatshirt()
      ),
      "pl"
    )).isInstanceOf(OrderProductUnavailableException.class);
  }

  @Test
  void shouldRejectUnavailableConfiguration() {
    var item = new OrderItemRequest.Sweatshirt(
      "embroidered-002",
      "pattern-001",
      new SweatshirtConfigurationRequest(
        "men", "92", "black", "small-front"
      ),
      2
    );

    assertThatThrownBy(() -> pricingService.priceItems(
      List.of(item),
      "pl"
    )).isInstanceOf(OrderProductUnavailableException.class);
  }

  @Test
  void shouldRejectSweatshirtSubmittedAsDigitalProduct() {
    assertThatThrownBy(() -> pricingService.priceItems(
      List.of(new OrderItemRequest.Digital("embroidered-002", 1)),
      "pl"
    )).isInstanceOf(OrderProductUnavailableException.class);
  }

  private OrderItemRequest.Sweatshirt sweatshirt() {
    return new OrderItemRequest.Sweatshirt(
      "embroidered-002",
      "pattern-001",
      new SweatshirtConfigurationRequest(
        "men", "M", "black", "small-front"
      ),
      2
    );
  }
}
