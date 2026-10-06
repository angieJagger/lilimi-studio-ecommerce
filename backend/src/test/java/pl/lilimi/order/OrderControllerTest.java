package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private OrderItemRepository orderItemRepository;

  @Test
  void shouldCreateDigitalOrder() throws Exception {
    var result = mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "pattern-001",
          "customer@example.com",
          """
          {"kind": "digital"}
          """
        )))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").isNotEmpty())
      .andExpect(jsonPath("$.createdAt").isNotEmpty())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.currency").value("PLN"))
      .andExpect(jsonPath("$.subtotalInGrosz").value(2900))
      .andExpect(jsonPath("$.deliveryPriceInGrosz").value(0))
      .andExpect(jsonPath("$.totalInGrosz").value(2900))
      .andReturn();

    var json = JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString());

    var orderId = UUID.fromString(json.get("id").asText());

    assertThat(orderRepository.findById(orderId)).isPresent();

    var items = orderItemRepository
      .findByOrder_IdOrderByPositionAsc(orderId);

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().getProductId())
      .isEqualTo("pattern-001");
  }

  @Test
  void shouldReturnConflictForUnavailableProduct() throws Exception {
    long ordersBefore = orderRepository.count();

    mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "missing-product",
          "customer@example.com",
          """
          {"kind": "digital"}
          """
        )))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code")
        .value("ORDER_PRODUCT_UNAVAILABLE"))
      .andExpect(jsonPath("$.productId").value("missing-product"));

    assertThat(orderRepository.count()).isEqualTo(ordersBefore);
  }

  @Test
  void shouldReturnValidationErrorForInvalidEmail() throws Exception {
    mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "pattern-001",
          "invalid-email",
          """
          {"kind": "digital"}
          """
        )))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code")
        .value("ORDER_VALIDATION_FAILED"))
      .andExpect(jsonPath("$.fields")
        .value(hasItem("contact.email")));
  }

  @Test
  void shouldRejectCourierForDigitalOnlyOrder() throws Exception {
    mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "pattern-001",
          "customer@example.com",
          """
          {
            "kind": "courier",
            "methodId": "dhl-courier",
            "address": {
              "addressLine1": "Testowa 10",
              "postalCode": "65-001",
              "city": "Zielona Góra",
              "countryCode": "PL"
            }
          }
          """
        )))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code")
        .value("ORDER_DELIVERY_INVALID"));
  }

  @Test
  void shouldRejectUnknownDeliveryKind() throws Exception {
    mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "pattern-001",
          "customer@example.com",
          """
          {"kind": "unknown"}
          """
        )))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code")
        .value("ORDER_REQUEST_INVALID"));
  }

  @Test
  void shouldRejectSameKeyUsedForDifferentOrderData() throws Exception {
    var key = UUID.randomUUID().toString();

    long ordersBefore = orderRepository.count();
    long itemsBefore = orderItemRepository.count();

    mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "pattern-001",
          "customer@example.com",
          """
          {"kind": "digital"}
          """
        )))
      .andExpect(status().isCreated());

    mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "pattern-001",
          "different@example.com",
          """
          {"kind": "digital"}
          """
        )))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code")
        .value("ORDER_SUBMISSION_CONFLICT"));

    assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
    assertThat(orderItemRepository.count()).isEqualTo(itemsBefore + 1);
  }

  private String request(
    String productId,
    String email,
    String delivery
  ) {
    return """
            {
              "language": "pl",
              "contact": {
                "fullName": "Test Customer",
                "email": "%s"
              },
              "delivery": %s,
              "items": [
                {
                  "kind": "digital",
                  "productId": "%s",
                  "quantity": 1
                }
              ]
            }
            """.formatted(email, delivery, productId);
  }

  @Test
  void shouldCreateMixedOrderWithCourierDelivery() throws Exception {
    var body = """
        {
          "language": "en",
          "contact": {
            "fullName": "Test Customer",
            "email": "customer@example.com"
          },
          "delivery": {
            "kind": "courier",
            "methodId": "dpd-courier",
            "address": {
              "addressLine1": "Testowa 10",
              "postalCode": "65-001",
              "city": "Zielona Góra",
              "countryCode": "PL"
            }
          },
          "items": [
            {
              "kind": "digital",
              "productId": "pattern-001",
              "quantity": 1
            },
            {
              "kind": "sweatshirt",
              "productId": "embroidered-002",
              "patternId": "pattern-001",
              "configuration": {
                "fit": "men",
                "size": "M",
                "color": "black",
                "embroideryOptionId": "large-back"
              },
              "quantity": 2
            }
          ]
        }
        """;

    var result = mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.subtotalInGrosz").value(36700))
      .andExpect(jsonPath("$.deliveryPriceInGrosz").value(1200))
      .andExpect(jsonPath("$.totalInGrosz").value(37900))
      .andReturn();

    var json = JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString());

    var orderId = UUID.fromString(json.get("id").asText());

    var savedOrder = orderRepository.findById(orderId)
      .orElseThrow();

    assertThat(savedOrder.getDelivery().getMethodId())
      .isEqualTo("dpd-courier");

    var items = orderItemRepository
      .findByOrder_IdOrderByPositionAsc(orderId);

    assertThat(items)
      .extracting(item -> item.getPosition())
      .containsExactly(1, 2);

    var sweatshirt = items.get(1);

    assertThat(sweatshirt.getKind()).isEqualTo("sweatshirt");
    assertThat(sweatshirt.getEmbroideryOptionId())
      .isEqualTo("large-back");
    assertThat(sweatshirt.getQuantity()).isEqualTo(2);
    assertThat(sweatshirt.getUnitPriceInGrosz()).isEqualTo(16900);
    assertThat(sweatshirt.getLineTotalInGrosz()).isEqualTo(33800);
  }

  @Test
  void shouldReturnSameOrderForRepeatedSubmission() throws Exception {
    var key = UUID.randomUUID().toString();

    var body = request(
      "pattern-001",
      "customer@example.com",
      """
      {"kind": "digital"}
      """
    );

    long ordersBefore = orderRepository.count();
    long itemsBefore = orderItemRepository.count();

    var first = mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isCreated())
      .andReturn();

    var second = mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isCreated())
      .andReturn();

    var mapper = JsonMapper.builder().build();

    var firstResponse = mapper.readTree(
      first.getResponse().getContentAsString()
    );

    var secondResponse = mapper.readTree(
      second.getResponse().getContentAsString()
    );

    assertThat(secondResponse).isEqualTo(firstResponse);
    assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
    assertThat(orderItemRepository.count()).isEqualTo(itemsBefore + 1);
  }
}
