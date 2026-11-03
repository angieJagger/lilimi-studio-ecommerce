package pl.lilimi.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.application.ResendDigitalDownloadLinkService;
import pl.lilimi.security.application.PublicFormRateLimiter;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false",
  "lilimi.mail.digital-downloads.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class ResendDigitalDownloadLinkControllerTest {

  private static final String URL =
    "/api/digital-downloads/resend";

  private static final UUID ORDER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ResendDigitalDownloadLinkService service;

  @MockitoBean
  private PublicFormRateLimiter limiter;

  @Test
  void shouldAcceptRequestWithoutLoginAndReturnNoDetails()
    throws Exception {

    mvc.perform(post(URL)
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(validRequest()))
      .andExpect(status().isAccepted())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(content().string(""));

    verify(service).request(ORDER_ID, "customer@example.com");
    verify(limiter).reserveDownloadLink("127.0.0.1");
  }

  @Test
  void shouldRequireCsrfBeforeCheckingLimit() throws Exception {
    mvc.perform(post(URL)
        .contentType(MediaType.APPLICATION_JSON)
        .content(validRequest()))
      .andExpect(status().isForbidden());

    verifyNoInteractions(service, limiter);
  }

  @Test
  void shouldRejectInvalidEmail() throws Exception {
    mvc.perform(post(URL)
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "orderId": "%s",
            "email": "invalid"
          }
          """.formatted(ORDER_ID)))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void shouldRejectMissingOrderId() throws Exception {
    mvc.perform(post(URL)
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "email": "customer@example.com"
          }
          """))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void shouldRejectRequestWhenSourceLimitIsExhausted()
    throws Exception {

    when(limiter.reserveDownloadLink("127.0.0.1"))
      .thenReturn(120L);

    mvc.perform(post(URL)
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(validRequest()))
      .andExpect(status().isTooManyRequests())
      .andExpect(header().string("Retry-After", "120"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.code")
        .value("PUBLIC_FORM_RATE_LIMITED"));

    verifyNoInteractions(service);
  }

  private String validRequest() {
    return """
      {
        "orderId": "%s",
        "email": "customer@example.com"
      }
      """.formatted(ORDER_ID);
  }
}
