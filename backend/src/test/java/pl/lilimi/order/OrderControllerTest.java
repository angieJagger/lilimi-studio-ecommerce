package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.DigitalPackageTestData;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.application.OrderStockReservationService;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;

import tools.jackson.databind.json.JsonMapper;


import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;


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

  @Autowired
  private UserAccountRepository accountRepository;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private OrderStockReservationService stockReservations;

  @Autowired
  private PaymentAttemptRepository paymentAttempts;

  @BeforeEach
  void prepareDigitalPackage() {
    DigitalPackageTestData.currentPackageId(
      jdbc,
      "pattern-001"
    );
  }

  @Test
  void shouldCreateDigitalOrder() throws Exception {
    var result = mockMvc.perform(post("/api/orders")
        .with(csrf())
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

    var savedOrder = orderRepository.findById(orderId).orElseThrow();
    assertThat(savedOrder.getCustomerAccountId()).isNull();

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
        .with(csrf())
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
        .with(csrf())
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
        .with(csrf())
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
        .with(csrf())
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
        .with(csrf())
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
        .with(csrf())
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
  void shouldPublishAndSellPhysicalProductWithSelectedOptions() throws Exception {
    var created = mockMvc.perform(multipart("/api/admin/products")
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf())
        .file(new MockMultipartFile("metadata", "", "application/json", """
          {
            "productType": "physical",
            "stockQuantity": 120,
            "pl": {"name": "Breloczek testowy", "description": "Breloczek."},
            "en": {"name": "Test keyring", "description": "A keyring."},
            "physicalConfiguration": {
              "priceInGrosz": 2490,
              "variantAttributes": [{"name":"Color", "values":["Green","Black"]}]
            }
          }
          """.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.active").value(true))
      .andReturn().getResponse();
    var mapper = JsonMapper.builder().build();
    var productId = mapper.readTree(created.getContentAsString()).get("id").asText();

    mockMvc.perform(get("/api/products/breloczek-testowy"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.priceInGrosz").value(2490))
      .andExpect(jsonPath("$.variantAttributes[0].values[0]").value("Green"));

    mockMvc.perform(get("/api/products/breloczek-testowy/quantity-check").param("quantity", "1"))
      .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/products/breloczek-testowy/quantity-check").param("quantity", "121"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.maxQuantity").value(120));

    var body = """
      {
        "language": "en",
        "contact": {"fullName": "Test Customer", "email": "customer@example.com"},
        "delivery": {
          "kind": "courier", "methodId": "dpd-courier",
          "address": {"addressLine1": "Testowa 10", "postalCode": "65-001", "city": "Zielona Góra", "countryCode": "PL"}
        },
        "items": [
          {"kind": "physical", "productId": "%s", "options": {"Color": "Green"}, "quantity": 120}
        ]
      }
      """.formatted(productId);
    mockMvc.perform(post("/api/orders")
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body.replace("Green", "Blue")))
      .andExpect(status().isConflict());

    mockMvc.perform(post("/api/orders")
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body.replace("\"quantity\": 120", "\"quantity\": 121")))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("ORDER_STOCK_LIMIT"))
      .andExpect(jsonPath("$.maxQuantity").value(120));

    var response = mockMvc.perform(post("/api/orders")
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.subtotalInGrosz").value(298800))
      .andExpect(jsonPath("$.deliveryPriceInGrosz").value(1200))
      .andExpect(jsonPath("$.totalInGrosz").value(300000))
      .andReturn().getResponse();

    var orderId = UUID.fromString(mapper.readTree(response.getContentAsString()).get("id").asText());
    assertThat(jdbc.queryForObject(
      "SELECT physical_options FROM order_items WHERE order_id = ?", String.class, orderId
    )).isEqualTo("Color: Green");

    mockMvc.perform(post("/api/orders")
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isConflict());

    mockMvc.perform(get("/api/products/breloczek-testowy"))
      .andExpect(status().isNotFound());

    mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"status": "cancelled", "expectedVersion": 0}
          """))
      .andExpect(status().isOk());

    mockMvc.perform(get(created.getHeader("Location"))
        .with(user("admin@example.com").roles("ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stockQuantity").value(120))
      .andExpect(jsonPath("$.active").value(true));

    mockMvc.perform(get("/api/products/breloczek-testowy"))
      .andExpect(status().isOk());

    var secondOrder = mockMvc.perform(post("/api/orders")
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isCreated())
      .andReturn().getResponse();
    var secondOrderId = UUID.fromString(mapper.readTree(secondOrder.getContentAsString()).get("id").asText());

    var now = Instant.now();
    var attempt = new PaymentAttempt(secondOrderId, UUID.randomUUID(), 300000, "PLN", false, now);
    attempt.attachCheckoutSession("cs_test_stock_reservation", now.plusSeconds(1800));
    attempt = paymentAttempts.saveAndFlush(attempt);

    assertThat(orderRepository.findDueStockReservationOrderIds(now.minusSeconds(3600)))
      .doesNotContain(secondOrderId);
    assertThat(orderRepository.findDueStockReservationOrderIds(now)).contains(secondOrderId);

    stockReservations.processDueOrder(secondOrderId, now);
    orderRepository.flush();

    var expiringOrder = orderRepository.findById(secondOrderId).orElseThrow();
    assertThat(expiringOrder.getStatus().getValue()).isEqualTo("new");
    assertThat(expiringOrder.isReservationExpirationRequested()).isTrue();
    mockMvc.perform(get("/api/products/breloczek-testowy"))
      .andExpect(status().isNotFound());

    attempt.markExpired("cs_test_stock_reservation");
    paymentAttempts.flush();
    assertThat(orderRepository.findDueStockReservationOrderIds(now)).contains(secondOrderId);

    stockReservations.processDueOrder(secondOrderId, now);
    stockReservations.processDueOrder(secondOrderId, now);
    orderRepository.flush();

    assertThat(orderRepository.findDueStockReservationOrderIds(now)).doesNotContain(secondOrderId);
    mockMvc.perform(get(created.getHeader("Location"))
        .with(user("admin@example.com").roles("ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stockQuantity").value(120));
    mockMvc.perform(get("/api/products/breloczek-testowy"))
      .andExpect(status().isOk());
  }

  @Test
  void shouldCreateMixedOrderWithCourierDelivery() throws Exception {
    var variantId = jdbc.queryForObject("""
      SELECT id FROM garment_variants
      WHERE product_id = 'embroidered-002' AND pattern_id = 'pattern-001'
        AND fit = 'men' AND size = 'M' AND color = 'black'
        AND embroidery_option_id = 'large-back'
      """, UUID.class);
    mockMvc.perform(get("/api/products/embroidered-sweatshirt/quantity-check")
        .param("quantity", "2").param("variantId", variantId.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.maxQuantity").value(1));

    mockMvc.perform(patch("/api/admin/products/embroidered-002/variants/{id}", variantId)
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"priceInGrosz": 16900, "stockQuantity": 2, "active": true, "expectedVersion": 0}
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stockQuantity").value(2));

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
        .with(csrf())
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
    assertThat(sweatshirt.getReservedVariantId()).isEqualTo(variantId);

    mockMvc.perform(get("/api/products/embroidered-sweatshirt/quantity-check")
        .param("quantity", "1").param("variantId", variantId.toString()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.maxQuantity").value(0));

    var variantVersion = jdbc.queryForObject("SELECT version FROM garment_variants WHERE id = ?", Long.class, variantId);
    mockMvc.perform(delete("/api/admin/products/embroidered-002/variants/{id}", variantId)
        .param("expectedVersion", Long.toString(variantVersion))
        .with(user("admin@example.com").roles("ADMIN")).with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.archived").value(true));

    mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"status": "cancelled", "expectedVersion": 0}
          """))
      .andExpect(status().isOk());

    mockMvc.perform(get("/api/products/embroidered-sweatshirt/quantity-check")
        .param("quantity", "3").param("variantId", variantId.toString()))
      .andExpect(status().isNotFound());
    orderRepository.flush();
    assertThat(jdbc.queryForObject(
      "SELECT stock_quantity FROM garment_variants WHERE id = ? AND archived = true AND active = false",
      Integer.class, variantId
    )).isEqualTo(2);
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
        .with(csrf())
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isCreated())
      .andReturn();

    var second = mockMvc.perform(post("/api/orders")
        .with(csrf())
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

  @Test
  void shouldAssignOrderToAuthenticatedAccount() throws Exception {
    var account = accountRepository.saveAndFlush(new UserAccount(
      "order-owner@example.com",
      "{noop}unused-test-password",
      UserRole.CUSTOMER
    ));

    var result = mockMvc.perform(post("/api/orders")
        .with(user(account.getEmail()).roles("CUSTOMER"))
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(request(
          "pattern-001",
          "different-contact@example.com",
          """
          {"kind": "digital"}
          """
        )))
      .andExpect(status().isCreated())
      .andReturn();

    var response = JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString());

    var orderId = UUID.fromString(response.get("id").asText());
    var savedOrder = orderRepository.findById(orderId).orElseThrow();

    assertThat(savedOrder.getCustomerAccountId())
      .isEqualTo(account.getId());
    assertThat(savedOrder.getContact().getEmail())
      .isEqualTo("different-contact@example.com");
  }

  @Test
  void shouldRejectRepeatedSubmissionByDifferentAccount() throws Exception {
    var owner = accountRepository.saveAndFlush(new UserAccount(
      "original-owner@example.com",
      "{noop}unused-test-password",
      UserRole.CUSTOMER
    ));

    var other = accountRepository.saveAndFlush(new UserAccount(
      "other-owner@example.com",
      "{noop}unused-test-password",
      UserRole.CUSTOMER
    ));

    var key = UUID.randomUUID().toString();
    var body = request(
      "pattern-001",
      "contact@example.com",
      """
      {"kind": "digital"}
      """
    );

    long ordersBefore = orderRepository.count();

    var first = mockMvc.perform(post("/api/orders")
        .with(user(owner.getEmail()).roles("CUSTOMER"))
        .with(csrf())
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isCreated())
      .andReturn();

    mockMvc.perform(post("/api/orders")
        .with(user(other.getEmail()).roles("CUSTOMER"))
        .with(csrf())
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("ORDER_SUBMISSION_CONFLICT"));

    var response = JsonMapper.builder().build()
      .readTree(first.getResponse().getContentAsString());

    var orderId = UUID.fromString(response.get("id").asText());
    var savedOrder = orderRepository.findById(orderId).orElseThrow();

    assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
    assertThat(savedOrder.getCustomerAccountId()).isEqualTo(owner.getId());
  }
}
