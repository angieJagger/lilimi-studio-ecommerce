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
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderContact;
import pl.lilimi.order.domain.OrderDelivery;
import pl.lilimi.order.persistence.OrderReadRepository;
import pl.lilimi.order.persistence.OrderRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminOrderReadControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private OrderRepository orders;

  @Autowired
  private OrderReadRepository reads;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldMarkReadForCurrentAdministratorWithoutChangingOrder()
    throws Exception {

    var admin = saveAccount(UserRole.ADMIN);
    var anotherAdmin = saveAccount(UserRole.ADMIN);
    var order = saveOrder();
    var originalStatus = order.getStatus();
    long originalVersion = order.getVersion();

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(
          post("/api/admin/orders/" + order.getId() + "/read")
            .with(user(admin.getEmail()).roles("ADMIN"))
            .with(csrf())
        )
        .andExpect(status().isNoContent());
    }

    assertThat(reads.findReadIds(admin.getId(), List.of(order.getId())))
      .containsExactly(order.getId());

    assertThat(reads.findReadIds(
      anotherAdmin.getId(),
      List.of(order.getId())
    )).isEmpty();

    assertThat(jdbc.queryForObject(
      """
      SELECT COUNT(*) FROM admin_order_reads
      WHERE administrator_id = ? AND order_id = ?
      """,
      Long.class,
      admin.getId(),
      order.getId()
    )).isEqualTo(1L);

    assertThat(jdbc.queryForObject(
      "SELECT status FROM orders WHERE id = ?",
      String.class,
      order.getId()
    )).isEqualTo(originalStatus.getValue());

    assertThat(jdbc.queryForObject(
      "SELECT version FROM orders WHERE id = ?",
      Long.class,
      order.getId()
    )).isEqualTo(originalVersion);
  }

  @Test
  void shouldReturnReadStateForCurrentAdministrator()
    throws Exception {

    var admin = saveAccount(UserRole.ADMIN);
    var anotherAdmin = saveAccount(UserRole.ADMIN);
    var order = saveOrder();

    // Fetching the list must not mark an order as read.
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc.perform(
          get("/api/admin/orders")
            .param("view", "all")
            .param("search", order.getId().toString())
            .with(user(admin.getEmail()).roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(
          order.getId().toString()
        ))
        .andExpect(jsonPath("$.items[0].read").value(false));
    }

    assertThat(reads.findReadIds(
      admin.getId(),
      List.of(order.getId())
    )).isEmpty();

    mockMvc.perform(
        post("/api/admin/orders/" + order.getId() + "/read")
          .with(user(admin.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNoContent());

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "all")
          .param("search", order.getId().toString())
          .with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(true));

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "all")
          .param("search", order.getId().toString())
          .with(user(anotherAdmin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(false));
  }

  @Test
  void shouldKeepOtherOrdersUnread() throws Exception {
    var admin = saveAccount(UserRole.ADMIN);
    var firstOrder = saveOrder();
    var secondOrder = saveOrder();

    mockMvc.perform(
        post("/api/admin/orders/" + firstOrder.getId() + "/read")
          .with(user(admin.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNoContent());

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "all")
          .param("search", firstOrder.getId().toString())
          .with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(true));

    mockMvc.perform(
        get("/api/admin/orders")
          .param("view", "all")
          .param("search", secondOrder.getId().toString())
          .with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].read").value(false));
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        post("/api/admin/orders/" + UUID.randomUUID() + "/read")
          .with(csrf())
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    var customer = saveAccount(UserRole.CUSTOMER);

    mockMvc.perform(
        post("/api/admin/orders/" + UUID.randomUUID() + "/read")
          .with(user(customer.getEmail()).roles("CUSTOMER"))
          .with(csrf())
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldRequireCsrfWithoutSavingReadReceipt() throws Exception {
    var admin = saveAccount(UserRole.ADMIN);
    var order = saveOrder();

    mockMvc.perform(
        post("/api/admin/orders/" + order.getId() + "/read")
          .with(user(admin.getEmail()).roles("ADMIN"))
      )
      .andExpect(status().isForbidden());

    assertThat(reads.findReadIds(admin.getId(), List.of(order.getId())))
      .isEmpty();
  }

  @Test
  void shouldReturnNotFoundWithoutSavingReadReceipt() throws Exception {
    var admin = saveAccount(UserRole.ADMIN);
    UUID missingId = UUID.randomUUID();

    mockMvc.perform(
        post("/api/admin/orders/" + missingId + "/read")
          .with(user(admin.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isNotFound());

    assertThat(reads.findReadIds(admin.getId(), List.of(missingId)))
      .isEmpty();
  }

  @Test
  void shouldCheckAdministratorRoleInDatabase() throws Exception {
    var customer = saveAccount(UserRole.CUSTOMER);
    var order = saveOrder();

    // The database role must be checked even with a forged test authority.
    mockMvc.perform(
        post("/api/admin/orders/" + order.getId() + "/read")
          .with(user(customer.getEmail()).roles("ADMIN"))
          .with(csrf())
      )
      .andExpect(status().isForbidden());

    assertThat(reads.findReadIds(
      customer.getId(),
      List.of(order.getId())
    )).isEmpty();
  }

  private UserAccount saveAccount(UserRole role) {
    return accounts.saveAndFlush(
      new UserAccount(
        UUID.randomUUID() + "@example.com",
        "test-password-hash",
        role
      )
    );
  }

  private Order saveOrder() {
    return orders.saveAndFlush(
      new Order(
        "pl",
        new OrderContact("Anna Kowalska", "anna@example.com", null),
        OrderDelivery.digital(),
        2900,
        0
      )
    );
  }
}
