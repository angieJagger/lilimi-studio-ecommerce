package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.security.application.PublicFormRateLimiter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class OrderRateLimitControllerTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JdbcTemplate jdbc;

  @MockitoBean
  private PublicFormRateLimiter limiter;

  @Test
  void shouldReturn429WithoutSavingOrderOrSubmission()
    throws Exception {

    UUID key = UUID.randomUUID();

    long ordersBefore = jdbc.queryForObject(
      "SELECT COUNT(*) FROM orders",
      Long.class
    );

    when(limiter.reserveOrder("127.0.0.1"))
      .thenReturn(300L);

    mvc.perform(post("/api/orders")
        .with(csrf())
        .header("Idempotency-Key", key.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(request()))
      .andExpect(status().isTooManyRequests())
      .andExpect(header().string("Retry-After", "300"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.code").value("ORDER_RATE_LIMITED"));

    verify(limiter).reserveOrder("127.0.0.1");

    assertThat(jdbc.queryForObject(
      "SELECT COUNT(*) FROM orders",
      Long.class
    )).isEqualTo(ordersBefore);

    assertThat(jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM order_submissions
      WHERE idempotency_key = ?
      """,
      Long.class,
      key
    )).isZero();
  }

  @Test
  void shouldRejectMissingCsrfBeforeCheckingOrderLimit()
    throws Exception {

    mvc.perform(post("/api/orders")
        .header(
          "Idempotency-Key",
          UUID.randomUUID().toString()
        )
        .contentType(MediaType.APPLICATION_JSON)
        .content(request()))
      .andExpect(status().isForbidden());

    verifyNoInteractions(limiter);
  }

  private String request() {
    return """
      {
        "language": "en",
        "contact": {
          "fullName": "Test Customer",
          "email": "order-limit@example.com",
          "phone": null
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
      """;
  }
}
