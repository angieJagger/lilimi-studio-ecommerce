package pl.lilimi.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.order.application.DigitalOrderDownload;
import pl.lilimi.order.application.DigitalOrderDownloadService;

import java.util.UUID;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class DigitalDownloadLinkControllerTest {

  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final String RAW_TOKEN = "a".repeat(43);

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private DigitalOrderDownloadService downloads;

  @Test
  void shouldPassTokenToServiceWithoutRequiringLogin() throws Exception {
    byte[] content = {1, 2, 3};

    when(downloads.downloadWithToken(RAW_TOKEN, ITEM_ID))
      .thenReturn(new DigitalOrderDownload(
        new ByteArrayResource(content),
        "dragon.zip",
        content.length
      ));

    mvc.perform(get(url())
        .header("X-Digital-Download-Token", RAW_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().contentType("application/zip"))
      .andExpect(content().bytes(content))
      .andExpect(header().string("Content-Length", "3"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(header().string("Referrer-Policy", "no-referrer"))
      .andExpect(header().string(
        "Content-Disposition",
        containsString("attachment")
      ))
      .andExpect(header().string(
        "Content-Disposition",
        containsString("dragon.zip")
      ));

    verify(downloads).downloadWithToken(RAW_TOKEN, ITEM_ID);
  }

  @Test
  void shouldRejectMissingToken() throws Exception {
    when(downloads.downloadWithToken(null, ITEM_ID))
      .thenThrow(new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "DIGITAL_DOWNLOAD_LINK_INVALID"
      ));

    mvc.perform(get(url()))
      .andExpect(status().isNotFound());

    verify(downloads).downloadWithToken(null, ITEM_ID);
  }

  @Test
  void shouldRejectInvalidToken() throws Exception {
    when(downloads.downloadWithToken("invalid", ITEM_ID))
      .thenThrow(new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "DIGITAL_DOWNLOAD_LINK_INVALID"
      ));

    mvc.perform(get(url())
        .header("X-Digital-Download-Token", "invalid"))
      .andExpect(status().isNotFound());

    verify(downloads).downloadWithToken("invalid", ITEM_ID);
  }

  @Test
  void shouldListDownloadsWithoutRequiringLogin() throws Exception {
    var file = new DigitalOrderDownloadResponse(
      ITEM_ID,
      "Leśny smok",
      "dragon.zip",
      2,
      2048L
    );

    when(downloads.findDownloadsWithToken(RAW_TOKEN))
      .thenReturn(List.of(file));

    mvc.perform(get("/api/digital-downloads")
        .header("X-Digital-Download-Token", RAW_TOKEN))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith("application/json"))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(header().string("Referrer-Policy", "no-referrer"))
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].itemId").value(ITEM_ID.toString()))
      .andExpect(jsonPath("$[0].productName").value("Leśny smok"))
      .andExpect(jsonPath("$[0].filename").value("dragon.zip"))
      .andExpect(jsonPath("$[0].packageVersion").value(2))
      .andExpect(jsonPath("$[0].byteSize").value(2048))
      .andExpect(jsonPath("$[0].storageKey").doesNotExist())
      .andExpect(jsonPath("$[0].rawToken").doesNotExist());

    verify(downloads).findDownloadsWithToken(RAW_TOKEN);
  }

  @Test
  void shouldRejectListingWithoutToken() throws Exception {
    when(downloads.findDownloadsWithToken(null))
      .thenThrow(new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "DIGITAL_DOWNLOAD_LINK_INVALID"
      ));

    mvc.perform(get("/api/digital-downloads"))
      .andExpect(status().isNotFound());

    verify(downloads).findDownloadsWithToken(null);
  }

  private String url() {
    return "/api/digital-downloads/items/"
      + ITEM_ID
      + "/download";
  }
}
