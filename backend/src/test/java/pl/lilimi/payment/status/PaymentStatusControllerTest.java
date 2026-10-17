package pl.lilimi.payment.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.access.GuestOrderAccessService;
import pl.lilimi.payment.domain.PaymentAttempt;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class PaymentStatusControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private PaymentAttemptRepository attempts;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private GuestOrderAccessService guestAccess;

  @Test
  void shouldReturnPendingStatusForAuthorizedGuest()
    throws Exception {

    var order = createOrder(null);
    var attempt = createAttempt(order);
    var session = new MockHttpSession();

    guestAccess.grant(session, order.getId());

    mockMvc.perform(get(url(attempt.getId()))
        .session(session))
      .andExpect(status().isOk())
      .andExpect(header().string(
        "Cache-Control",
        "no-store"
      ))
      .andExpect(jsonPath("$.paymentAttemptId")
        .value(attempt.getId().toString()))
      .andExpect(jsonPath("$.status").value("pending"))
      .andExpect(jsonPath("$.checkoutUrl").doesNotExist());
  }

  @Test
  void shouldReturnConfirmedStatusForAccountOwner()
    throws Exception {

    var account = createAccount();
    var order = createOrder(account);
    var attempt = createAttempt(order);

    attempt.confirmPayment(
      attempt.getStripeCheckoutSessionId(),
      "pi_test_" + UUID.randomUUID(),
      2900,
      "PLN",
      false,
      Instant.now()
    );

    attempts.saveAndFlush(attempt);

    mockMvc.perform(get(url(attempt.getId()))
        .with(user(account.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("succeeded"));
  }

  @Test
  void shouldRejectDifferentGuestSession()
    throws Exception {

    var order = createOrder(null);
    var attempt = createAttempt(order);
    var originalSession = new MockHttpSession();

    guestAccess.grant(originalSession, order.getId());

    mockMvc.perform(get(url(attempt.getId()))
        .session(new MockHttpSession()))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectGuestWithoutSession()
    throws Exception {

    var attempt = createAttempt(createOrder(null));

    mockMvc.perform(get(url(attempt.getId())))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectDifferentAccount()
    throws Exception {

    var owner = createAccount();
    var other = createAccount();
    var attempt = createAttempt(createOrder(owner));

    mockMvc.perform(get(url(attempt.getId()))
        .with(user(other.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldNotReplaceAccountOwnershipWithGuestGrant()
    throws Exception {

    var owner = createAccount();
    var order = createOrder(owner);
    var attempt = createAttempt(order);
    var session = new MockHttpSession();

    guestAccess.grant(session, order.getId());

    mockMvc.perform(get(url(attempt.getId()))
        .session(session))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectUnknownAttempt() throws Exception {
    mockMvc.perform(get(url(UUID.randomUUID())))
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectMalformedAttemptId() throws Exception {
    mockMvc.perform(get(
        "/api/payments/attempts/not-a-uuid"
      ))
      .andExpect(status().isBadRequest());
  }

  private UserAccount createAccount() {
    var account = new UserAccount(
      "status-" + UUID.randomUUID() + "@example.com",
      "unused-test-password-hash",
      UserRole.CUSTOMER
    );

    account.requireEmailVerification();
    account.verifyEmail();

    return accounts.saveAndFlush(account);
  }

  private Order createOrder(UserAccount account) {
    return orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        account == null
          ? "guest@example.com"
          : account.getEmail(),
        null
      ),
      OrderDelivery.digital(),
      2900,
      0,
      account == null ? null : account.getId()
    ));
  }

  private PaymentAttempt createAttempt(Order order) {
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

    return attempts.saveAndFlush(attempt);
  }

  private static String url(UUID attemptId) {
    return "/api/payments/attempts/" + attemptId;
  }
}
