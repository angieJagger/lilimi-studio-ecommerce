package pl.lilimi.payment.checkout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pl.lilimi.DigitalPackageTestData;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderRepository;

import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class OrderPaymentControllerTest {

  private static final String CHECKOUT_URL =
    "https://checkout.stripe.com/c/pay/test-example";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private StripeCheckoutService checkout;

  private final JsonMapper mapper =
    JsonMapper.builder().build();

  @BeforeEach
  void prepareDigitalPackage() {
    DigitalPackageTestData.currentPackageId(
      jdbc,
      "pattern-001"
    );
  }

  @Test
  void shouldAllowGuestAfterSubmittingOrderInSameSession()
    throws Exception {

    var session = new MockHttpSession();
    UUID orderId = submitGuestOrder(session);
    UUID key = UUID.randomUUID();

    stubCheckout(orderId, key);

    mockMvc.perform(post(paymentUrl(orderId))
        .session(session)
        .with(csrf())
        .header("Idempotency-Key", key))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("pending"))
      .andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL));

    verify(checkout).start(orderId, key);
  }

  @Test
  void shouldRejectGuestUsingDifferentSession()
    throws Exception {

    UUID orderId = submitGuestOrder(
      new MockHttpSession()
    );

    mockMvc.perform(post(paymentUrl(orderId))
        .session(new MockHttpSession())
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isNotFound());

    verifyNoInteractions(checkout);
  }

  @Test
  void shouldAllowSignedInOrderOwner() throws Exception {
    var account = createAccount();
    var order = createOwnedOrder(account);
    UUID key = UUID.randomUUID();

    stubCheckout(order.getId(), key);

    mockMvc.perform(post(paymentUrl(order.getId()))
        .with(user(account.getEmail()).roles("CUSTOMER"))
        .with(csrf())
        .header("Idempotency-Key", key))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL));

    verify(checkout).start(order.getId(), key);
  }

  @Test
  void shouldRejectAnotherSignedInCustomer() throws Exception {
    var owner = createAccount();
    var other = createAccount();
    var order = createOwnedOrder(owner);

    mockMvc.perform(post(paymentUrl(order.getId()))
        .with(user(other.getEmail()).roles("CUSTOMER"))
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isNotFound());

    verifyNoInteractions(checkout);
  }

  @Test
  void shouldRequireCsrfEvenForAuthorizedGuest()
    throws Exception {

    var session = new MockHttpSession();
    UUID orderId = submitGuestOrder(session);

    mockMvc.perform(post(paymentUrl(orderId))
        .session(session)
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isForbidden());

    verifyNoInteractions(checkout);
  }

  @Test
  void shouldRequireIdempotencyKey() throws Exception {
    var session = new MockHttpSession();
    UUID orderId = submitGuestOrder(session);

    mockMvc.perform(post(paymentUrl(orderId))
        .session(session)
        .with(csrf()))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(checkout);
  }

  @Test
  void shouldRejectMalformedIdempotencyKey()
    throws Exception {

    var session = new MockHttpSession();
    UUID orderId = submitGuestOrder(session);

    mockMvc.perform(post(paymentUrl(orderId))
        .session(session)
        .with(csrf())
        .header("Idempotency-Key", "not-a-uuid"))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(checkout);
  }

  @Test
  void shouldRejectUnknownOrder() throws Exception {
    mockMvc.perform(post(paymentUrl(UUID.randomUUID()))
        .session(new MockHttpSession())
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isNotFound());

    verifyNoInteractions(checkout);
  }

  private UUID submitGuestOrder(
    MockHttpSession session
  ) throws Exception {
    var result = mockMvc.perform(post("/api/orders")
        .session(session)
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "language": "pl",
            "contact": {
              "fullName": "Test Customer",
              "email": "payment-test@example.com"
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
          """))
      .andExpect(status().isCreated())
      .andReturn();

    var body = mapper.readTree(
      result.getResponse().getContentAsString()
    );

    return UUID.fromString(body.get("id").asText());
  }

  private UserAccount createAccount() {
    var account = new UserAccount(
      "payment-" + UUID.randomUUID() + "@example.com",
      "unused-test-password-hash",
      UserRole.CUSTOMER
    );

    account.requireEmailVerification();
    account.verifyEmail();

    return accounts.saveAndFlush(account);
  }

  private Order createOwnedOrder(UserAccount account) {
    return orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        account.getEmail(),
        null
      ),
      OrderDelivery.digital(),
      2900,
      0,
      account.getId()
    ));
  }

  private void stubCheckout(UUID orderId, UUID key) {
    when(checkout.start(orderId, key))
      .thenReturn(new CheckoutSessionResult(
        UUID.randomUUID(),
        "pending",
        CHECKOUT_URL
      ));
  }

  private String paymentUrl(UUID orderId) {
    return "/api/orders/" + orderId + "/payment-session";
  }

  @Test
  void shouldResumePaymentForAuthorizedGuest()
    throws Exception {

    var session = new MockHttpSession();
    UUID orderId = submitGuestOrder(session);
    UUID key = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();

    when(checkout.resume(orderId, key))
      .thenReturn(new CheckoutSessionResult(
        attemptId,
        "pending",
        CHECKOUT_URL
      ));

    mockMvc.perform(post(paymentUrl(orderId) + "/resume")
        .session(session)
        .with(csrf())
        .header("Idempotency-Key", key))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.paymentAttemptId")
        .value(attemptId.toString()))
      .andExpect(jsonPath("$.status").value("pending"))
      .andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL));

    verify(checkout).resume(orderId, key);
  }

  @Test
  void shouldResumePaymentForAccountOwner()
    throws Exception {

    var account = createAccount();
    var order = createOwnedOrder(account);
    UUID key = UUID.randomUUID();

    when(checkout.resume(order.getId(), key))
      .thenReturn(new CheckoutSessionResult(
        UUID.randomUUID(),
        "pending",
        CHECKOUT_URL
      ));

    mockMvc.perform(post(paymentUrl(order.getId()) + "/resume")
        .with(user(account.getEmail()).roles("CUSTOMER"))
        .with(csrf())
        .header("Idempotency-Key", key))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL));

    verify(checkout).resume(order.getId(), key);
  }

  @Test
  void shouldRejectResumptionFromDifferentGuestSession()
    throws Exception {

    UUID orderId = submitGuestOrder(
      new MockHttpSession()
    );

    mockMvc.perform(post(paymentUrl(orderId) + "/resume")
        .session(new MockHttpSession())
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isNotFound());

    verifyNoInteractions(checkout);
  }

  @Test
  void shouldRejectResumptionByDifferentAccount()
    throws Exception {

    var owner = createAccount();
    var other = createAccount();
    var order = createOwnedOrder(owner);

    mockMvc.perform(post(paymentUrl(order.getId()) + "/resume")
        .with(user(other.getEmail()).roles("CUSTOMER"))
        .with(csrf())
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isNotFound());

    verifyNoInteractions(checkout);
  }

  @Test
  void shouldRequireCsrfForPaymentResumption()
    throws Exception {

    var session = new MockHttpSession();
    UUID orderId = submitGuestOrder(session);

    mockMvc.perform(post(paymentUrl(orderId) + "/resume")
        .session(session)
        .header("Idempotency-Key", UUID.randomUUID()))
      .andExpect(status().isForbidden());

    verifyNoInteractions(checkout);
  }
}
