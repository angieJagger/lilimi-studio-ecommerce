package pl.lilimi.customer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.persistence.UserAccountRepository;
import pl.lilimi.customer.persistence.CustomerProfileRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class CustomerProfileControllerTest {

  private static final String URL = "/api/account/profile";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserAccountRepository accounts;

  @Autowired
  private CustomerProfileRepository profiles;

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final List<UUID> accountIds = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    // Delete profiles first so cleanup also works without relying on cascades.
    profiles.deleteAllById(accountIds);
    accounts.deleteAllById(accountIds);
  }

  @Test
  void shouldRejectAnonymousReadAndWrite() throws Exception {
    mockMvc.perform(get(URL))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(put(URL).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectAdminAccess() throws Exception {
    var account = createAccount(UserRole.ADMIN);

    mockMvc.perform(get(URL)
        .with(user(account.getEmail()).roles("ADMIN")))
      .andExpect(status().isForbidden());

    mockMvc.perform(put(URL).with(csrf())
        .with(user(account.getEmail()).roles("ADMIN"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnEmptyProfileWithoutCreatingRow() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);

    read(account)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(account.getEmail()))
      .andExpect(jsonPath("$.firstName").doesNotExist())
      .andExpect(jsonPath("$.version").doesNotExist());

    assertThat(profiles.existsById(account.getId())).isFalse();
  }

  @Test
  void shouldSaveAndReadNormalizedProfile() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    var body = contact();
    body.put("firstName", "  Anna  ");
    body.put("addressLine1", "  Testowa 12/3  ");
    body.put("addressLine2", "   ");
    body.put("postalCode", "00-001");
    body.put("city", " Warszawa ");
    body.put("countryCode", "PL");

    write(account, body)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(0));

    read(account)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Anna"))
      .andExpect(jsonPath("$.lastName").value("Kowalska"))
      .andExpect(jsonPath("$.addressLine1").value("Testowa 12/3"))
      .andExpect(jsonPath("$.addressLine2").doesNotExist())
      .andExpect(jsonPath("$.city").value("Warszawa"))
      .andExpect(jsonPath("$.countryCode").value("PL"));
  }

  @Test
  void shouldUpdateMatchingVersionAndRejectStaleWrite() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    write(account, contact()).andExpect(status().isOk());

    var body = contact();
    body.put("version", 0);
    body.put("firstName", "Maria");
    write(account, body)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(1));

    body.put("firstName", "Stale value");
    write(account, body).andExpect(status().isConflict());

    read(account)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Maria"))
      .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void shouldRejectMissingVersionForExistingProfile() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    write(account, contact()).andExpect(status().isOk());
    write(account, contact()).andExpect(status().isConflict());
  }

  @Test
  void shouldRejectVersionForProfileThatDoesNotExist() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    var body = contact();
    body.put("version", 0);

    write(account, body).andExpect(status().isConflict());
    assertThat(profiles.existsById(account.getId())).isFalse();
  }

  @Test
  void shouldRejectIncompleteAddressWithoutSavingContactChanges() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    write(account, contact()).andExpect(status().isOk());

    var body = contact();
    body.put("version", 0);
    body.put("firstName", "Must roll back");
    body.put("addressLine1", "Testowa 12");
    write(account, body).andExpect(status().isBadRequest());

    read(account)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Anna"))
      .andExpect(jsonPath("$.version").value(0));
  }

  @Test
  void shouldRejectInvalidPostalCodeAndUnsupportedCountry() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    var body = contact();
    body.put("addressLine1", "Testowa 12");
    body.put("postalCode", "00001");
    body.put("city", "Warszawa");
    body.put("countryCode", "PL");
    write(account, body).andExpect(status().isBadRequest());

    body.put("postalCode", "00-001");
    body.put("countryCode", "DE");
    write(account, body).andExpect(status().isBadRequest());

    assertThat(profiles.existsById(account.getId())).isFalse();
  }

  @Test
  void shouldAllowClearingSavedAddress() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    var body = contact();
    body.put("addressLine1", "Testowa 12");
    body.put("postalCode", "00-001");
    body.put("city", "Warszawa");
    body.put("countryCode", "PL");
    write(account, body).andExpect(status().isOk());

    var cleared = contact();
    cleared.put("version", 0);
    write(account, cleared)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.addressLine1").doesNotExist())
      .andExpect(jsonPath("$.postalCode").doesNotExist())
      .andExpect(jsonPath("$.city").doesNotExist())
      .andExpect(jsonPath("$.countryCode").doesNotExist());
  }

  @Test
  void shouldRejectNamesTooLongForOrderContact() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    var body = contact();
    body.put("firstName", "a".repeat(75));
    body.put("lastName", "b".repeat(75));

    write(account, body).andExpect(status().isBadRequest());
    assertThat(profiles.existsById(account.getId())).isFalse();
  }

  @Test
  void shouldRequireCsrfForWrite() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);

    mockMvc.perform(put(URL)
        .with(user(account.getEmail()).roles("CUSTOMER"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(contact())))
      .andExpect(status().isForbidden());

    assertThat(profiles.existsById(account.getId())).isFalse();
  }

  @Test
  void shouldKeepProfilesSeparateForDifferentCustomers() throws Exception {
    var owner = createAccount(UserRole.CUSTOMER);
    var other = createAccount(UserRole.CUSTOMER);
    write(owner, contact()).andExpect(status().isOk());

    read(other)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(other.getEmail()))
      .andExpect(jsonPath("$.firstName").doesNotExist());

    var otherBody = contact();
    otherBody.put("firstName", "Other customer");
    write(other, otherBody).andExpect(status().isOk());

    read(owner)
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.firstName").value("Anna"));
  }

  @Test
  void shouldRejectUnverifiedCustomer() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    account.requireEmailVerification();
    accounts.saveAndFlush(account);

    read(account).andExpect(status().isForbidden());
    write(account, contact()).andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectDisabledCustomer() throws Exception {
    var account = createAccount(UserRole.CUSTOMER);
    account.disable();
    accounts.saveAndFlush(account);

    read(account).andExpect(status().isUnauthorized());
    write(account, contact()).andExpect(status().isUnauthorized());
  }

  private UserAccount createAccount(UserRole role) {
    var account = new UserAccount(
      "profile-" + UUID.randomUUID() + "@example.com",
      "unused-test-password-hash",
      role
    );

    if (role == UserRole.CUSTOMER) {
      account.requireEmailVerification();
      account.verifyEmail();
    }

    var saved = accounts.saveAndFlush(account);
    accountIds.add(saved.getId());
    return saved;
  }

  private Map<String, Object> contact() {
    var body = new LinkedHashMap<String, Object>();
    body.put("firstName", "Anna");
    body.put("lastName", "Kowalska");
    body.put("phone", "+48123456789");
    return body;
  }

  private ResultActions read(UserAccount account) throws Exception {
    return mockMvc.perform(get(URL)
      .with(user(account.getEmail()).roles("CUSTOMER")));
  }

  private ResultActions write(
    UserAccount account,
    Map<String, Object> body
  ) throws Exception {
    return mockMvc.perform(put(URL).with(csrf())
      .with(user(account.getEmail()).roles("CUSTOMER"))
      .contentType(MediaType.APPLICATION_JSON)
      .content(mapper.writeValueAsString(body)));
  }
}
