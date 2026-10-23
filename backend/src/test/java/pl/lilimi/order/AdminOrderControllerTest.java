package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;
import pl.lilimi.payment.cancellation.CheckoutExpirationQueue;

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

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminOrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private PaymentAttemptRepository payments;
  @Autowired
  private CheckoutExpirationQueue expirationQueue;

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

  private PaymentAttempt savePaidPayment(Order order) {
    Instant createdAt = Instant.now().minusSeconds(60);

    var attempt = new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      order.getTotalInGrosz(),
      order.getCurrency(),
      false,
      createdAt
    );

    String sessionId = "cs_test_" + UUID.randomUUID();

    attempt.attachCheckoutSession(
      sessionId,
      createdAt.plusSeconds(1800)
    );

    attempt.confirmPayment(
      sessionId,
      "pi_test_" + UUID.randomUUID(),
      order.getTotalInGrosz(),
      order.getCurrency(),
      false,
      Instant.now()
    );

    return payments.saveAndFlush(attempt);
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
  void shouldCombineStatusAndDateFiltersBeforePagination()
    throws Exception {

    var older = saveOrder(
      "filtered-older@example.com",
      2900,
      Instant.parse("2090-01-10T09:00:00Z")
    );

    var newer = saveOrder(
      "filtered-newer@example.com",
      3900,
      Instant.parse("2090-01-10T12:00:00Z")
    );

    var processing = saveOrder(
      "filtered-processing@example.com",
      4900,
      Instant.parse("2090-01-10T15:00:00Z")
    );

    changeStatus(processing.getId(), 0, "processing")
      .andExpect(status().isOk());

    saveOrder(
      "filtered-outside@example.com",
      5900,
      Instant.parse("2090-01-11T12:00:00Z")
    );

    mockMvc.perform(
        get("/api/admin/orders")
          .param("status", "new")
          .param("dateFrom", "2090-01-10")
          .param("dateTo", "2090-01-10")
          .param("size", "1")
          .param("page", "0")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].id").value(
        newer.getId().toString()
      ))
      .andExpect(jsonPath("$.totalElements").value(2))
      .andExpect(jsonPath("$.totalPages").value(2));

    mockMvc.perform(
        get("/api/admin/orders")
          .param("status", "new")
          .param("dateFrom", "2090-01-10")
          .param("dateTo", "2090-01-10")
          .param("size", "1")
          .param("page", "1")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].id").value(
        older.getId().toString()
      ))
      .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void shouldRespectWarsawDayBoundariesDuringTimeChange()
    throws Exception {

    // Warsaw changes from UTC+1 to UTC+2 on this day.
    saveOrder(
      "before-day@example.com",
      2900,
      Instant.parse("2026-03-28T22:59:59.999999Z")
    );

    var first = saveOrder(
      "day-start@example.com",
      2900,
      Instant.parse("2026-03-28T23:00:00Z")
    );

    var last = saveOrder(
      "day-end@example.com",
      2900,
      Instant.parse("2026-03-29T21:59:59.999999Z")
    );

    saveOrder(
      "after-day@example.com",
      2900,
      Instant.parse("2026-03-29T22:00:00Z")
    );

    mockMvc.perform(
        get("/api/admin/orders")
          .param("dateFrom", "2026-03-29")
          .param("dateTo", "2026-03-29")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(2))
      .andExpect(jsonPath("$.items.length()").value(2))
      .andExpect(jsonPath("$.items[0].id").value(
        last.getId().toString()
      ))
      .andExpect(jsonPath("$.items[1].id").value(
        first.getId().toString()
      ));
  }

  @Test
  void shouldFilterByStatusWithoutDateLimits()
    throws Exception {

    var cancelled = saveOrder(
      "cancelled-filter@example.com",
      2900,
      Instant.parse("2090-02-01T12:00:00Z")
    );

    changeStatus(cancelled.getId(), 0, "cancelled")
      .andExpect(status().isOk());

    saveOrder(
      "new-filter@example.com",
      2900,
      Instant.parse("2090-02-02T12:00:00Z")
    );

    mockMvc.perform(
        get("/api/admin/orders")
          .param("status", "cancelled")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id").value(
        cancelled.getId().toString()
      ))
      .andExpect(jsonPath(
        "$.items[?(@.status != 'cancelled')]"
      ).isEmpty());
  }

  @Test
  void shouldReturnEmptyPageWhenNoOrdersMatch()
    throws Exception {

    mockMvc.perform(
        get("/api/admin/orders")
          .param("dateFrom", "2089-06-01")
          .param("dateTo", "2089-06-01")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isEmpty())
      .andExpect(jsonPath("$.totalElements").value(0))
      .andExpect(jsonPath("$.totalPages").value(0));
  }
  @Test
  void shouldSeparateActiveAndArchivedOrders() throws Exception {
    Instant createdAt = Instant.parse("2091-01-10T12:00:00Z");

    var fresh = saveOrder(
      "view-new@example.com", 2900, createdAt
    );
    var processing = saveOrder(
      "view-processing@example.com", 2900, createdAt
    );
    var completed = saveOrder(
      "view-completed@example.com", 2900, createdAt
    );
    var cancelled = saveOrder(
      "view-cancelled@example.com", 2900, createdAt
    );

    changeStatus(processing.getId(), 0, "processing")
      .andExpect(status().isOk());

    changeStatus(completed.getId(), 0, "processing")
      .andExpect(status().isOk());
    changeStatus(completed.getId(), 1, "completed")
      .andExpect(status().isOk());

    changeStatus(cancelled.getId(), 0, "cancelled")
      .andExpect(status().isOk());

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "active")
          .param("dateFrom", "2091-01-10")
          .param("dateTo", "2091-01-10")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(2))
      .andExpect(jsonPath(
        "$.items[?(@.id == '" + fresh.getId() + "')]"
      ).isNotEmpty())
      .andExpect(jsonPath(
        "$.items[?(@.id == '" + processing.getId() + "')]"
      ).isNotEmpty());

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "archive")
          .param("dateFrom", "2091-01-10")
          .param("dateTo", "2091-01-10")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(2))
      .andExpect(jsonPath(
        "$.items[?(@.id == '" + completed.getId() + "')]"
      ).isNotEmpty())
      .andExpect(jsonPath(
        "$.items[?(@.id == '" + cancelled.getId() + "')]"
      ).isNotEmpty());

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "all")
          .param("dateFrom", "2091-01-10")
          .param("dateTo", "2091-01-10")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(4));
  }

  @Test
  void shouldKeepCancelledOrderRequiringReviewInActiveView()
    throws Exception {

    var order = saveOrder(
      "active-review@example.com",
      2900,
      Instant.parse("2091-02-10T12:00:00Z")
    );

    changeStatus(order.getId(), 0, "cancelled")
      .andExpect(status().isOk());

    var payment = savePaidPayment(order);
    expirationQueue.flagPaymentAfterCancellation(payment.getId());

    for (String view : new String[] { "active", "archive", "all" }) {
      mockMvc.perform(
          get("/api/admin/orders")
            .param("view", view)
            .param("dateFrom", "2091-02-10")
            .param("dateTo", "2091-02-10")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          order.getId().toString()
        ))
        .andExpect(jsonPath("$.items[0].status").value("cancelled"))
        .andExpect(jsonPath("$.items[0].paymentCancellation.status")
          .value("needs_review"));
    }

    // Explicit filters still narrow the selected view.
    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "active")
          .param("status", "new")
          .param("dateFrom", "2091-02-10")
          .param("dateTo", "2091-02-10")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(0));

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "active")
          .param("dateFrom", "2091-02-11")
          .param("dateTo", "2091-02-11")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void shouldSearchByOrderNumberEmailAndCustomerName()
    throws Exception {

    var order = saveOrder(
      "search-customer@example.com",
      2900,
      Instant.parse("2092-01-10T12:00:00Z")
    );

    String[] phrases = {
      order.getId().toString(),
      order.getId().toString().substring(0, 8),
      "SEARCH-CUSTOMER@EXAMPLE.COM",
      "customer@",
      "KOWALSKA",
      "  Anna Kowalska  "
    };

    for (String phrase : phrases) {
      mockMvc.perform(
          get("/api/admin/orders")
            .param("view", "all")
            .param("search", phrase)
            .param("dateFrom", "2092-01-10")
            .param("dateTo", "2092-01-10")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          order.getId().toString()
        ));
    }
  }

  @Test
  void shouldApplySearchBeforePaginationAndRespectOtherFilters()
    throws Exception {

    var older = saveOrder(
      "phone-search-older@example.com",
      2900,
      Instant.parse("2092-02-10T09:00:00Z")
    );

    var newer = saveOrder(
      "phone-search-newer@example.com",
      3900,
      Instant.parse("2092-02-10T12:00:00Z")
    );

    var cancelled = saveOrder(
      "phone-search-cancelled@example.com",
      4900,
      Instant.parse("2092-02-10T15:00:00Z")
    );

    changeStatus(cancelled.getId(), 0, "cancelled")
      .andExpect(status().isOk());

    saveOrder(
      "unrelated@example.com",
      5900,
      Instant.parse("2092-02-10T16:00:00Z")
    );

    saveOrder(
      "phone-search-outside@example.com",
      6900,
      Instant.parse("2092-02-11T12:00:00Z")
    );

    String[] expectedIds = {
      newer.getId().toString(),
      older.getId().toString()
    };

    for (int page = 0; page < expectedIds.length; page++) {
      mockMvc.perform(
          get("/api/admin/orders")
            .param("view", "all")
            .param("status", "new")
            .param("search", "phone-search")
            .param("dateFrom", "2092-02-10")
            .param("dateTo", "2092-02-10")
            .param("size", "1")
            .param("page", Integer.toString(page))
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(expectedIds[page]))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2));
    }

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "archive")
          .param("search", "phone-search")
          .param("dateFrom", "2092-02-10")
          .param("dateTo", "2092-02-10")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalElements").value(1))
      .andExpect(jsonPath("$.items[0].id").value(
        cancelled.getId().toString()
      ));
  }

  @Test
  void shouldTreatSearchWildcardsAsLiteralCharacters()
    throws Exception {

    var literal = saveOrder(
      "literal%_!@example.com",
      2900,
      Instant.parse("2092-03-10T12:00:00Z")
    );

    saveOrder(
      "ordinary@example.com",
      3900,
      Instant.parse("2092-03-10T13:00:00Z")
    );

    for (String phrase : new String[] { "%", "_", "!", "%_!" }) {
      mockMvc.perform(
          get("/api/admin/orders")
            .param("view", "all")
            .param("search", phrase)
            .param("dateFrom", "2092-03-10")
            .param("dateTo", "2092-03-10")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          literal.getId().toString()
        ));
    }

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "all")
          .param("search", "' OR 1=1 --")
          .param("dateFrom", "2092-03-10")
          .param("dateTo", "2092-03-10")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isEmpty())
      .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void shouldIgnoreBlankSearch() throws Exception {
    var order = saveOrder(
      "blank-search@example.com",
      2900,
      Instant.parse("2092-04-10T12:00:00Z")
    );

    for (String phrase : new String[] { "", "   " }) {
      mockMvc.perform(
          get("/api/admin/orders")
            .param("search", phrase)
            .param("dateFrom", "2092-04-10")
            .param("dateTo", "2092-04-10")
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          order.getId().toString()
        ));
    }
  }

  @Test
  void shouldRejectSearchLongerThanLimit() throws Exception {
    mockMvc.perform(
        get("/api/admin/orders")
          .param("search", "a".repeat(255))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isBadRequest());
  }
  @Test
  void shouldRejectInvalidOrderFilters() throws Exception {
    for (String query : new String[] {
      "?status=unknown",
      "?status=",
      "?view=unknown",
      "?dateFrom=invalid",
      "?dateTo=2026-02-30",
      "?dateFrom=2026-10-22&dateTo=2026-10-21"
    }) {
      mockMvc.perform(
          get("/api/admin/orders" + query)
            .with(user("admin@example.com").roles("ADMIN"))
        )
        .andExpect(status().isBadRequest());
    }
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
  void shouldRejectCancellationOfPaidOrderWithoutChangingData()
    throws Exception {

    var order = saveOrder(
      "paid-cancellation@example.com",
      2900,
      Instant.now()
    );
    var payment = savePaidPayment(order);

    changeStatus(order.getId(), order.getVersion(), "cancelled")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "ORDER_PAYMENT_REFUND_REQUIRED"
      ));

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.version").value(0));

    var savedPayment = payments.findById(payment.getId())
      .orElseThrow();

    assertThat(savedPayment.getStatus())
      .isEqualTo(PaymentAttemptStatus.SUCCEEDED);
    assertThat(savedPayment.getStripePaymentIntentId())
      .isEqualTo(payment.getStripePaymentIntentId());
  }

  @Test
  void shouldAllowProcessingPaidOrderButStillRejectCancellation()
    throws Exception {

    var order = saveOrder(
      "paid-processing@example.com",
      2900,
      Instant.now()
    );
    savePaidPayment(order);

    changeStatus(order.getId(), 0, "processing")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("processing"))
      .andExpect(jsonPath("$.version").value(1));

    changeStatus(order.getId(), 1, "cancelled")
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "ORDER_PAYMENT_REFUND_REQUIRED"
      ));

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("processing"))
      .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void shouldAllowCancellationAfterPaymentCreationFailed()
    throws Exception {

    var order = saveOrder(
      "failed-payment-cancellation@example.com",
      2900,
      Instant.now()
    );

    var attempt = new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      order.getTotalInGrosz(),
      order.getCurrency(),
      false,
      Instant.now()
    );

    attempt.markCreationFailed();
    payments.saveAndFlush(attempt);

    changeStatus(order.getId(), order.getVersion(), "cancelled")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("cancelled"))
      .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void shouldReturnNoCancellationTaskForRegularOrder()
    throws Exception {

    var order = saveOrder(
      "no-expiration-task@example.com",
      2900,
      Instant.now()
    );

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.paymentCancellation.status")
        .value("none"))
      .andExpect(jsonPath("$.paymentCancellation.reasonCode")
        .doesNotExist());

    mockMvc.perform(
        get("/api/admin/orders")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id")
        .value(order.getId().toString()))
      .andExpect(jsonPath("$.items[0].paymentCancellation.status")
        .value("none"));
  }

  @Test
  void shouldReturnPendingExpirationInDetailsAndHistory()
    throws Exception {

    var order = saveOrder(
      "pending-expiration@example.com",
      2900,
      Instant.now()
    );

    Instant createdAt = Instant.now().minusSeconds(60);

    var attempt = new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      createdAt
    );

    attempt.attachCheckoutSession(
      "cs_test_" + UUID.randomUUID(),
      createdAt.plusSeconds(1800)
    );

    payments.saveAndFlush(attempt);

    changeStatus(order.getId(), 0, "cancelled")
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.paymentCancellation.status")
        .value("pending"));

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("cancelled"))
      .andExpect(jsonPath("$.paymentCancellation.status")
        .value("pending"))
      .andExpect(jsonPath("$.paymentCancellation.leaseToken")
        .doesNotExist());

    mockMvc.perform(
        get("/api/admin/orders")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id")
        .value(order.getId().toString()))
      .andExpect(jsonPath("$.items[0].paymentCancellation.status")
        .value("pending"));
  }

  @Test
  void shouldKeepReviewVisibleDespiteAnotherCompletedTask()
    throws Exception {

    var order = saveOrder(
      "review-expiration@example.com",
      2900,
      Instant.now()
    );

    changeStatus(order.getId(), 0, "cancelled")
      .andExpect(status().isOk());

    // Simulate payment confirmed after cancellation.
    var paid = savePaidPayment(order);

    expirationQueue.flagPaymentAfterCancellation(
      paid.getId()
    );

    var another = new PaymentAttempt(
      order.getId(),
      UUID.randomUUID(),
      2900,
      "PLN",
      false,
      Instant.now()
    );

    another.markCreationFailed();
    payments.saveAndFlush(another);

    jdbc.update(
      """
      INSERT INTO checkout_expiration_tasks (
        payment_attempt_id,
        status,
        finished_at
      )
      VALUES (?, 'completed', CURRENT_TIMESTAMP)
      """,
      another.getId()
    );

    mockMvc.perform(
        get("/api/admin/orders/" + order.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("cancelled"))
      .andExpect(jsonPath("$.paymentCancellation.status")
        .value("needs_review"))
      .andExpect(jsonPath("$.paymentCancellation.reasonCode")
        .value("PAYMENT_AFTER_CANCELLATION"));

    mockMvc.perform(
        get("/api/admin/orders")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id")
        .value(order.getId().toString()))
      .andExpect(jsonPath("$.items[0].paymentCancellation.status")
        .value("needs_review"))
      .andExpect(jsonPath("$.items[0].paymentCancellation.reasonCode")
        .value("PAYMENT_AFTER_CANCELLATION"));
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
