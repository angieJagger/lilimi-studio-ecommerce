package pl.lilimi.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.DigitalPackageTestData;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.catalog.digital.DigitalProductPackageStorage;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.access.GuestOrderAccessService;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class DigitalOrderDownloadControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private OrderItemRepository items;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private GuestOrderAccessService guestAccess;

  @Autowired
  private UserAccountRepository accounts;

  @MockitoBean
  private PaymentAttemptRepository payments;

  @MockitoBean
  private DigitalProductPackageStorage storage;

  private UUID orderId;
  private UUID itemId;
  private UUID packageId;

  @BeforeEach
  void prepareOrder() {
    packageId = DigitalPackageTestData.createHistoricalPackage(
      jdbc,
      "pattern-001"
    );

    var order = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        "customer@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    ));

    var item = items.saveAndFlush(OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Leśny smok",
      packageId,
      2900
    ));

    orderId = order.getId();
    itemId = item.getId();
  }

  @Test
  void shouldDownloadFileForAuthorizedGuest() throws Exception {
    var session = new MockHttpSession();
    guestAccess.grant(session, orderId);

    when(payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    var content = new byte[128];

    when(storage.load(packageId + ".zip"))
      .thenReturn(new ByteArrayResource(content));

    mvc.perform(get(downloadUrl()).session(session))
      .andExpect(status().isOk())
      .andExpect(content().contentType("application/zip"))
      .andExpect(content().bytes(content))
      .andExpect(header().string(
        HttpHeaders.CONTENT_LENGTH,
        "128"
      ))
      .andExpect(header().string(
        HttpHeaders.CONTENT_DISPOSITION,
        containsString("attachment")
      ))
      .andExpect(header().string(
        HttpHeaders.CONTENT_DISPOSITION,
        containsString("test-package.zip")
      ))
      .andExpect(header().string(
        HttpHeaders.CACHE_CONTROL,
        containsString("no-store")
      ));
  }

  @Test
  void shouldRejectGuestWithoutAccess() throws Exception {
    mvc.perform(get(downloadUrl()))
      .andExpect(status().isNotFound());

    verifyNoInteractions(payments, storage);
  }

  @Test
  void shouldRejectDifferentGuestSession() throws Exception {
    var authorizedSession = new MockHttpSession();
    guestAccess.grant(authorizedSession, orderId);

    mvc.perform(get(downloadUrl()).session(new MockHttpSession()))
      .andExpect(status().isNotFound());

    verifyNoInteractions(payments, storage);
  }

  @Test
  void shouldAllowOrderOwnerToDownload() throws Exception {
    var owner = createAccount(UserRole.CUSTOMER);
    prepareOwnedOrder(owner);

    when(payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    when(storage.load(packageId + ".zip"))
      .thenReturn(new ByteArrayResource(new byte[128]));

    mvc.perform(get(downloadUrl())
        .with(user(owner.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isOk());
  }

  @Test
  void shouldRejectAnotherCustomer() throws Exception {
    var owner = createAccount(UserRole.CUSTOMER);
    var other = createAccount(UserRole.CUSTOMER);

    prepareOwnedOrder(owner);

    mvc.perform(get(downloadUrl())
        .with(user(other.getEmail()).roles("CUSTOMER")))
      .andExpect(status().isNotFound());

    verifyNoInteractions(payments, storage);
  }

  @Test
  void shouldNotGrantAdministratorAccessToAnotherAccountOrder()
    throws Exception {

    var owner = createAccount(UserRole.CUSTOMER);
    var administrator = createAccount(UserRole.ADMIN);

    prepareOwnedOrder(owner);

    mvc.perform(get(downloadUrl())
        .with(user(administrator.getEmail()).roles("ADMIN")))
      .andExpect(status().isNotFound());

    verifyNoInteractions(payments, storage);
  }

  @Test
  void shouldRejectDownloadBeforePayment() throws Exception {
    var session = new MockHttpSession();
    guestAccess.grant(session, orderId);

    when(payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(false);

    mvc.perform(get(downloadUrl()).session(session))
      .andExpect(status().isConflict());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectItemFromAnotherOrder() throws Exception {
    var session = new MockHttpSession();
    guestAccess.grant(session, orderId);

    when(payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    var otherOrder = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Another Customer",
        "another@example.com",
        null
      ),
      OrderDelivery.digital(),
      2900,
      0
    ));

    var otherItem = items.saveAndFlush(OrderItem.digital(
      otherOrder,
      1,
      "pattern-001",
      "Leśny smok",
      packageId,
      2900
    ));

    var manipulatedUrl = "/api/orders/" + orderId
      + "/items/" + otherItem.getId() + "/download";

    mvc.perform(get(manipulatedUrl).session(session))
      .andExpect(status().isNotFound());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldListPurchasedPackageForAuthorizedGuest() throws Exception {
    var session = new MockHttpSession();
    guestAccess.grant(session, orderId);

    when(payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    mvc.perform(get("/api/orders/" + orderId + "/downloads")
        .session(session))
      .andExpect(status().isOk())
      .andExpect(header().string(
        HttpHeaders.CACHE_CONTROL,
        containsString("no-store")
      ))
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].itemId").value(itemId.toString()))
      .andExpect(jsonPath("$[0].productName").value("Leśny smok"))
      .andExpect(jsonPath("$[0].filename").value("test-package.zip"))
      .andExpect(jsonPath("$[0].packageVersion").isNumber())
      .andExpect(jsonPath("$[0].byteSize").value(128))
      .andExpect(jsonPath("$[0].storageKey").doesNotExist())
      .andExpect(jsonPath("$[0].resource").doesNotExist());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectDownloadListWithoutOrderAccess() throws Exception {
    mvc.perform(get("/api/orders/" + orderId + "/downloads"))
      .andExpect(status().isNotFound());

    verifyNoInteractions(payments, storage);
  }

  @Test
  void shouldRejectDownloadListBeforePayment() throws Exception {
    var session = new MockHttpSession();
    guestAccess.grant(session, orderId);

    when(payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(false);

    mvc.perform(get("/api/orders/" + orderId + "/downloads")
        .session(session))
      .andExpect(status().isConflict());

    verifyNoInteractions(storage);
  }

  private UserAccount createAccount(UserRole role) {
    return accounts.saveAndFlush(new UserAccount(
      UUID.randomUUID() + "@example.com",
      "{noop}unused-test-password",
      role
    ));
  }

  private void prepareOwnedOrder(UserAccount owner) {
    var order = orders.saveAndFlush(new Order(
      "pl",
      new OrderContact(
        "Test Customer",
        owner.getEmail(),
        null
      ),
      OrderDelivery.digital(),
      2900,
      0,
      owner.getId()
    ));

    var item = items.saveAndFlush(OrderItem.digital(
      order,
      1,
      "pattern-001",
      "Leśny smok",
      packageId,
      2900
    ));

    orderId = order.getId();
    itemId = item.getId();
  }

  private String downloadUrl() {
    return "/api/orders/" + orderId
      + "/items/" + itemId + "/download";
  }
}
