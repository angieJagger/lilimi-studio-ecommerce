package pl.lilimi.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.PostgresTestConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  PostgresTestConfiguration.class,
  SecurityConfigTest.AdminProbeController.class
})
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldAllowPublicCatalogWithoutLogin() throws Exception {
    mockMvc.perform(get("/api/products"))
      .andExpect(status().isOk());
  }

  @Test
  void shouldRequireLoginForAdminEndpoint() throws Exception {
    mockMvc.perform(get("/api/admin/security-check"))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectUserWithoutAdminRole() throws Exception {
    mockMvc.perform(get("/api/admin/security-check")
        .with(user("customer").roles("CUSTOMER")))
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowAdministrator() throws Exception {
    mockMvc.perform(get("/api/admin/security-check")
        .with(user("admin").roles("ADMIN")))
      .andExpect(status().isOk())
      .andExpect(content().string("ok"));
  }

  @Test
  void shouldRejectOrderWithoutCsrfToken() throws Exception {
    mockMvc.perform(post("/api/orders")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldIssueCsrfCookieWithoutLogin() throws Exception {
    mockMvc.perform(get("/api/auth/csrf"))
      .andExpect(status().isNoContent())
      .andExpect(cookie().exists("XSRF-TOKEN"))
      .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
  }

  @Test
  void shouldAcceptCsrfCookieAndMatchingHeader() throws Exception {
    var result = mockMvc.perform(get("/api/auth/csrf"))
      .andExpect(status().isNoContent())
      .andExpect(cookie().exists("XSRF-TOKEN"))
      .andReturn();

    var csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrfCookie).isNotNull();

    mockMvc.perform(post("/api/orders")
        .cookie(csrfCookie)
        .header("X-XSRF-TOKEN", csrfCookie.getValue())
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectIncorrectCsrfHeader() throws Exception {
    var result = mockMvc.perform(get("/api/auth/csrf"))
      .andExpect(status().isNoContent())
      .andExpect(cookie().exists("XSRF-TOKEN"))
      .andReturn();

    var csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrfCookie).isNotNull();

    mockMvc.perform(post("/api/orders")
        .cookie(csrfCookie)
        .header("X-XSRF-TOKEN", "incorrect-token")
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden());
  }

  @RestController
  public static class AdminProbeController {

    @GetMapping("/api/admin/security-check")
    public String check() {
      return "ok";
    }
  }
}
