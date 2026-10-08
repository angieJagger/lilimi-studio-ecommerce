package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderRepository;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminOrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private JdbcTemplate jdbc;

  private Order saveOrder(
    String email,
    long amount,
    Instant createdAt
  ) {
    var order = orders.saveAndFlush(
      new Order(
        "pl",
        new OrderContact("Anna Kowalska", email, null),
        OrderDelivery.digital(),
        amount,
        0
      )
    );

    jdbc.update(
      "UPDATE orders SET created_at = ? WHERE id = ?",
      java.sql.Timestamp.from(createdAt),
      order.getId()
    );

    return order;
  }

  private ResultActions changeStatus(
    UUID id,
    long expectedVersion,
    String nextStatus
  ) throws Exception {
    return mockMvc.perform(
      patch("/api/admin/orders/" + id + "/status")
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf())
        .contentType("application/json")
        .content("""
        {
          "status": "%s",
          "expectedVersion": %d
        }
        """.formatted(nextStatus, expectedVersion))
    );
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(get("/api/admin/orders"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
      get("/api/admin/orders")
        .with(user("customer@example.com").roles("CUSTOMER"))
    ).andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowAdministrator() throws Exception {
    mockMvc.perform(
        get("/api/admin/orders")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.items").isArray());
  }

  @Test
  void shouldReturnNewestOrdersFirstWithPagination() throws Exception {
    long initialCount = orders.count();

    var older = saveOrder(
      "older@example.com",
      2900,
      Instant.parse("2090-01-01T10:00:00Z")
    );

    var newer = saveOrder(
      "newer@example.com",
      5900,
      Instant.parse("2090-01-02T10:00:00Z")
    );

    mockMvc.perform(
        get("/api/admin/orders")
          .param("page", "0")
          .param("size", "1")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].id").value(
        newer.getId().toString()
      ))
      .andExpect(jsonPath("$.items[0].status").value("new"))
      .andExpect(jsonPath("$.items[0].customerFullName")
        .value("Anna Kowalska"))
      .andExpect(jsonPath("$.items[0].customerEmail")
        .value("newer@example.com"))
      .andExpect(jsonPath("$.items[0].currency").value("PLN"))
      .andExpect(jsonPath("$.items[0].totalInGrosz").value(5900))
      .andExpect(jsonPath("$.totalElements").value(
        (int) (initialCount + 2)
      ))
      .andExpect(jsonPath("$.totalPages").value(
        (int) (initialCount + 2)
      ));

    mockMvc.perform(
        get("/api/admin/orders")
          .param("page", "1")
          .param("size", "1")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(1))
      .andExpect(jsonPath("$.items[0].id").value(
        older.getId().toString()
      ));
  }

  @Test
  void shouldRejectInvalidPagination() throws Exception {
    for (String query : new String[] {
      "?page=-1",
      "?size=0",
      "?size=101"
    }) {
      mockMvc.perform(
        get("/api/admin/orders" + query)
          .with(user("admin@example.com").roles("ADMIN"))
      ).andExpect(status().isBadRequest());
    }
  }
  @Test
  void shouldReturnOrderDetailsWithSavedItems() throws Exception {
    var creation = mockMvc.perform(
        post("/api/orders")
          .with(csrf())
          .header("Idempotency-Key", UUID.randomUUID().toString())
          .contentType("application/json")
          .content("""
        {
          "language": "pl",
          "contact": {
            "fullName": "Anna Kowalska",
            "email": "anna@example.com",
            "phone": "123456789"
          },
          "delivery": {
            "kind": "digital"
          },
          "items": [
            {
              "kind": "digital",
              "productId": "pattern-001",
              "quantity": 1
            }
          ]
        }
        """)
      )
      .andExpect(status().isCreated())
      .andReturn();

    String id = JsonMapper.builder().build()
      .readTree(creation.getResponse().getContentAsString())
      .get("id")
      .asText();

    mockMvc.perform(
        get("/api/admin/orders/" + id)
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(id))
      .andExpect(jsonPath("$.version").value(0))
      .andExpect(jsonPath("$.createdAt").isString())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.language").value("pl"))
      .andExpect(jsonPath("$.currency").value("PLN"))
      .andExpect(jsonPath("$.contact.fullName").value("Anna Kowalska"))
      .andExpect(jsonPath("$.contact.email").value("anna@example.com"))
      .andExpect(jsonPath("$.contact.phone").value("123456789"))
      .andExpect(jsonPath("$.delivery.kind").value("digital"))
      .andExpect(jsonPath("$.delivery.methodId").doesNotExist())
      .andExpect(jsonPath("$.delivery.addressLine1").doesNotExist())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].kind").value("digital"))
      .andExpect(jsonPath("$.items[0].productId").value("pattern-001"))
      .andExpect(jsonPath("$.items[0].productName").isNotEmpty())
      .andExpect(jsonPath("$.items[0].quantity").value(1))
      .andExpect(jsonPath("$.items[0].unitPriceInGrosz").value(2900))
      .andExpect(jsonPath("$.items[0].lineTotalInGrosz").value(2900))
      .andExpect(jsonPath("$.subtotalInGrosz").value(2900))
      .andExpect(jsonPath("$.deliveryPriceInGrosz").value(0))
      .andExpect(jsonPath("$.totalInGrosz").value(2900));
  }

  @Test
  void shouldRejectAnonymousUserReadingOrderDetails() throws Exception {
    mockMvc.perform(
      get("/api/admin/orders/" + UUID.randomUUID())
    ).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomerReadingOrderDetails() throws Exception {
    mockMvc.perform(
      get("/api/admin/orders/" + UUID.randomUUID())
        .with(user("customer@example.com").roles("CUSTOMER"))
    ).andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnNotFoundForMissingOrder() throws Exception {
    mockMvc.perform(
      get("/api/admin/orders/" + UUID.randomUUID())
        .with(user("admin@example.com").roles("ADMIN"))
    ).andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectInvalidOrderId() throws Exception {
    mockMvc.perform(
      get("/api/admin/orders/invalid-id")
        .with(user("admin@example.com").roles("ADMIN"))
    ).andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnSweatshirtConfigurationAndCourierAddress() throws Exception {
    var creation = mockMvc.perform(
        post("/api/orders")
          .with(csrf())
          .header("Idempotency-Key", UUID.randomUUID().toString())
          .contentType("application/json")
          .content("""
        {
          "language": "pl",
          "contact": {
            "fullName": "Anna Kowalska",
            "email": "anna@example.com"
          },
          "delivery": {
            "kind": "courier",
            "methodId": "dhl-courier",
            "address": {
              "addressLine1": "Testowa 10",
              "addressLine2": "Mieszkanie 2",
              "postalCode": "65-001",
              "city": "Zielona Góra",
              "countryCode": "PL"
            }
          },
          "items": [
            {
              "kind": "sweatshirt",
              "productId": "embroidered-002",
              "patternId": "pattern-001",
              "configuration": {
                "fit": "men",
                "size": "M",
                "color": "black",
                "embroideryOptionId": "small-front"
              },
              "quantity": 2
            }
          ]
        }
        """)
      )
      .andExpect(status().isCreated())
      .andReturn();

    String id = JsonMapper.builder().build()
      .readTree(creation.getResponse().getContentAsString())
      .get("id")
      .asText();

    mockMvc.perform(
        get("/api/admin/orders/" + id)
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.delivery.kind").value("courier"))
      .andExpect(jsonPath("$.delivery.methodId").value("dhl-courier"))
      .andExpect(jsonPath("$.delivery.addressLine1").value("Testowa 10"))
      .andExpect(jsonPath("$.delivery.addressLine2").value("Mieszkanie 2"))
      .andExpect(jsonPath("$.delivery.postalCode").value("65-001"))
      .andExpect(jsonPath("$.delivery.city").value("Zielona Góra"))
      .andExpect(jsonPath("$.delivery.countryCode").value("PL"))
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].kind").value("sweatshirt"))
      .andExpect(jsonPath("$.items[0].productId").value("embroidered-002"))
      .andExpect(jsonPath("$.items[0].patternId").value("pattern-001"))
      .andExpect(jsonPath("$.items[0].patternName").isNotEmpty())
      .andExpect(jsonPath("$.items[0].fit").value("men"))
      .andExpect(jsonPath("$.items[0].size").value("M"))
      .andExpect(jsonPath("$.items[0].color").value("black"))
      .andExpect(jsonPath("$.items[0].embroideryOptionId").value("small-front"))
      .andExpect(jsonPath("$.items[0].quantity").value(2))
      .andExpect(jsonPath("$.items[0].unitPriceInGrosz").value(14900))
      .andExpect(jsonPath("$.items[0].lineTotalInGrosz").value(29800))
      .andExpect(jsonPath("$.subtotalInGrosz").value(29800))
      .andExpect(jsonPath("$.deliveryPriceInGrosz").value(1200))
      .andExpect(jsonPath("$.totalInGrosz").value(31000));
  }

  @Test
  void shouldSaveStatusAndIncreaseVersion() throws Exception {
    var order = saveOrder(
      "status-test@example.com",
      2900,
      Instant.now()
    );

    changeStatus(order.getId(), 0, "processing")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("processing"))
      .andExpect(jsonPath("$.version").value(1));

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("processing"))
      .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void shouldRejectAnOutdatedVersionWithoutChangingStatus() throws Exception {
    var order = saveOrder(
      "version-test@example.com",
      2900,
      Instant.now()
    );

    changeStatus(order.getId(), 0, "processing")
      .andExpect(status().isOk());

    changeStatus(order.getId(), 0, "cancelled")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("ORDER_VERSION_CONFLICT"));

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("processing"))
      .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void shouldRejectAnInvalidStatusTransition() throws Exception {
    var order = saveOrder(
      "transition-test@example.com",
      2900,
      Instant.now()
    );

    changeStatus(order.getId(), 0, "completed")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "ORDER_STATUS_TRANSITION_INVALID"
      ));

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void shouldKeepVersionWhenSettingTheSameStatus() throws Exception {
    var order = saveOrder(
      "same-status@example.com",
      2900,
      Instant.now()
    );

    changeStatus(order.getId(), 0, "processing")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(1));

    changeStatus(order.getId(), 1, "processing")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("processing"))
      .andExpect(jsonPath("$.version").value(1));
  }
  @Test
  void shouldRejectAnonymousStatusChange() throws Exception {
    mockMvc.perform(
      patch("/api/admin/orders/" + UUID.randomUUID() + "/status")
        .with(csrf())
        .contentType("application/json")
        .content("""
        {
          "status": "processing",
          "expectedVersion": 0
        }
        """)
    ).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomerStatusChange() throws Exception {
    mockMvc.perform(
      patch("/api/admin/orders/" + UUID.randomUUID() + "/status")
        .with(user("customer@example.com").roles("CUSTOMER"))
        .with(csrf())
        .contentType("application/json")
        .content("""
        {
          "status": "processing",
          "expectedVersion": 0
        }
        """)
    ).andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectStatusChangeWithoutCsrfToken() throws Exception {
    mockMvc.perform(
      patch("/api/admin/orders/" + UUID.randomUUID() + "/status")
        .with(user("admin@example.com").roles("ADMIN"))
        .contentType("application/json")
        .content("""
        {
          "status": "processing",
          "expectedVersion": 0
        }
        """)
    ).andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnNotFoundWhenChangingMissingOrder() throws Exception {
    changeStatus(UUID.randomUUID(), 0, "processing")
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectInvalidStatusChangeData() throws Exception {
    var order = saveOrder(
      "validation-test@example.com",
      2900,
      Instant.now()
    );

    String[] invalidRequests = {
      """
    {"status": "unknown", "expectedVersion": 0}
    """,
      """
    {"status": "", "expectedVersion": 0}
    """,
      """
    {"expectedVersion": 0}
    """,
      """
    {"status": "processing"}
    """,
      """
    {"status": "processing", "expectedVersion": -1}
    """
    };

    for (String request : invalidRequests) {
      mockMvc.perform(
        patch("/api/admin/orders/" + order.getId() + "/status")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType("application/json")
          .content(request)
      ).andExpect(status().isBadRequest());
    }

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.version").value(0));
  }
}
