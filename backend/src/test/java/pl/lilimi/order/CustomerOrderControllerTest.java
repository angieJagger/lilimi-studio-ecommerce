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
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class CustomerOrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accountRepository;

  @Test
  void shouldRejectHistoryWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/account/orders"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnEmptyHistoryForNewCustomer() throws Exception {
    var account = createAccount("empty-history@example.com");

    mockMvc.perform(get("/api/account/orders")
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isEmpty())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.totalElements").value(0))
      .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  void shouldReturnOnlyOrdersOwnedByCurrentAccount() throws Exception {
    var owner = createAccount("history-owner@example.com");
    var other = createAccount("history-other@example.com");

    var ownedOrderId = createOrder(
      owner,
      "separate-contact@example.com"
    );

    // Matching contact email must not grant ownership.
    createOrder(other, owner.getEmail());
    createOrder(null, owner.getEmail());

    mockMvc.perform(get("/api/account/orders")
        .with(user(owner.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].id").value(ownedOrderId))
      .andExpect(jsonPath("$.items[0].status").value("new"))
      .andExpect(jsonPath("$.items[0].currency").value("PLN"))
      .andExpect(jsonPath("$.items[0].totalInGrosz").value(2900))
      .andExpect(jsonPath("$.items[0].customerAccountId").doesNotExist())
      .andExpect(jsonPath("$.items[0].customerEmail").doesNotExist())
      .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void shouldPaginateCustomerOrders() throws Exception {
    var account = createAccount("paginated-history@example.com");

    var expectedIds = new HashSet<String>();
    expectedIds.add(createOrder(account, account.getEmail()));
    expectedIds.add(createOrder(account, account.getEmail()));
    expectedIds.add(createOrder(account, account.getEmail()));

    var first = mockMvc.perform(get("/api/account/orders")
        .with(user(account.getEmail()).roles("CUSTOMER"))
        .param("page", "0")
        .param("size", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(2))
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(2))
      .andExpect(jsonPath("$.totalElements").value(3))
      .andExpect(jsonPath("$.totalPages").value(2))
      .andReturn();

    var second = mockMvc.perform(get("/api/account/orders")
        .with(user(account.getEmail()).roles("CUSTOMER"))
        .param("page", "1")
        .param("size", "2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.page").value(1))
      .andExpect(jsonPath("$.size").value(2))
      .andExpect(jsonPath("$.totalElements").value(3))
      .andExpect(jsonPath("$.totalPages").value(2))
      .andReturn();

    var mapper = JsonMapper.builder().build();

    var firstItems = mapper.readTree(
      first.getResponse().getContentAsString()
    ).get("items");

    var secondItems = mapper.readTree(
      second.getResponse().getContentAsString()
    ).get("items");

    var actualIds = new HashSet<String>();

    for (var item : firstItems) {
      assertThat(actualIds.add(item.get("id").asText())).isTrue();
    }

    for (var item : secondItems) {
      assertThat(actualIds.add(item.get("id").asText())).isTrue();
    }

    assertThat(actualIds).containsExactlyInAnyOrderElementsOf(expectedIds);
  }

  @Test
  void shouldRejectNegativePage() throws Exception {
    var account = createAccount("negative-page@example.com");

    mockMvc.perform(get("/api/account/orders")
        .with(user(account.getEmail()).roles("CUSTOMER"))
        .param("page", "-1"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectInvalidPageSize() throws Exception {
    var account = createAccount("invalid-page-size@example.com");

    for (var size : new String[]{"0", "101"}) {
      mockMvc.perform(get("/api/account/orders")
          .with(user(account.getEmail()).roles("CUSTOMER"))
          .param("size", size))
        .andExpect(status().isBadRequest());
    }
  }

  private UserAccount createAccount(String email) {
    return accountRepository.saveAndFlush(new UserAccount(
      email,
      "{noop}unused-test-password",
      UserRole.CUSTOMER
    ));
  }

  private String createOrder(
    UserAccount account,
    String contactEmail
  ) throws Exception {
    var request = post("/api/orders")
      .with(csrf())
      .header("Idempotency-Key", UUID.randomUUID().toString())
      .contentType(MediaType.APPLICATION_JSON)
      .content("""
        {
          "language": "pl",
          "contact": {
            "fullName": "History Test Customer",
            "email": "%s"
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
        """.formatted(contactEmail));

    if (account != null) {
      request.with(user(account.getEmail()).roles("CUSTOMER"));
    }

    var result = mockMvc.perform(request)
      .andExpect(status().isCreated())
      .andReturn();

    return JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString())
      .get("id")
      .asText();
  }
  @Test
  void shouldReturnDetailsOfOwnedOrder() throws Exception {
    var account = createAccount("order-details@example.com");
    var orderId = createOrder(account, "details-contact@example.com");

    mockMvc.perform(get("/api/account/orders/{id}", orderId)
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(orderId))
      .andExpect(jsonPath("$.createdAt").isNotEmpty())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.language").value("pl"))
      .andExpect(jsonPath("$.currency").value("PLN"))
      .andExpect(jsonPath("$.contact.fullName").value("History Test Customer"))
      .andExpect(jsonPath("$.contact.email").value("details-contact@example.com"))
      .andExpect(jsonPath("$.delivery.kind").value("digital"))
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].productId").value("pattern-001"))
      .andExpect(jsonPath("$.items[0].quantity").value(1))
      .andExpect(jsonPath("$.items[0].unitPriceInGrosz").value(2900))
      .andExpect(jsonPath("$.items[0].lineTotalInGrosz").value(2900))
      .andExpect(jsonPath("$.subtotalInGrosz").value(2900))
      .andExpect(jsonPath("$.deliveryPriceInGrosz").value(0))
      .andExpect(jsonPath("$.totalInGrosz").value(2900))
      .andExpect(jsonPath("$.customerAccountId").doesNotExist())
      .andExpect(jsonPath("$.version").doesNotExist());
  }

  @Test
  void shouldRejectOrderDetailsWithoutAuthentication() throws Exception {
    var account = createAccount("private-details@example.com");
    var orderId = createOrder(account, account.getEmail());

    mockMvc.perform(get("/api/account/orders/{id}", orderId))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldHideOtherCustomersOrderDetails() throws Exception {
    var owner = createAccount("details-owner@example.com");
    var other = createAccount("details-other@example.com");

    // Matching contact email does not grant access.
    var orderId = createOrder(owner, other.getEmail());

    mockMvc.perform(get("/api/account/orders/{id}", orderId)
        .with(user(other.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldHideGuestOrderDetailsEvenWhenContactEmailMatches()
    throws Exception {

    var account = createAccount("guest-details@example.com");
    var orderId = createOrder(null, account.getEmail());

    mockMvc.perform(get("/api/account/orders/{id}", orderId)
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isNotFound());
  }
  @Test
  void shouldReturnNotFoundForMissingOrder() throws Exception {
    var account = createAccount("missing-order@example.com");

    mockMvc.perform(get(
        "/api/account/orders/{id}",
        UUID.randomUUID()
      )
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectMalformedOrderId() throws Exception {
    var account = createAccount("invalid-order-id@example.com");

    mockMvc.perform(get("/api/account/orders/not-a-uuid")
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isBadRequest());
  }
}
