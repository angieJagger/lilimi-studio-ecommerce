package pl.lilimi.catalog.digital;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import pl.lilimi.PostgresTestConfiguration;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class AdminDigitalProductPackageControllerTest {

  private static final String PRODUCT_ID = "pattern-001";

  private static final String URL =
    "/api/admin/products/" + PRODUCT_ID + "/digital-packages";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminDigitalProductPackageService service;

  @Test
  void shouldAllowAdministratorAndHideStorageKey() throws Exception {
    var saved = new DigitalProductPackage(
      PRODUCT_ID,
      1,
      "internal-storage-key.zip",
      "dragon.zip",
      1024,
      "a".repeat(64),
      Instant.parse("2026-10-27T10:00:00Z")
    );
    saved.makeCurrent();

    when(service.upload(eq(PRODUCT_ID), any(MultipartFile.class)))
      .thenReturn(saved);

    mockMvc.perform(multipart(URL)
        .file(upload())
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf()))
      .andExpect(status().isCreated())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.id").value(saved.getId().toString()))
      .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
      .andExpect(jsonPath("$.packageVersion").value(1))
      .andExpect(jsonPath("$.originalFilename").value("dragon.zip"))
      .andExpect(jsonPath("$.byteSize").value(1024))
      .andExpect(jsonPath("$.sha256").value("a".repeat(64)))
      .andExpect(jsonPath("$.currentPackage").value(true))
      .andExpect(jsonPath("$.storageKey").doesNotExist())
      .andExpect(jsonPath("$.content").doesNotExist());
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(multipart(URL)
        .file(upload())
        .with(csrf()))
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(service);
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(multipart(URL)
        .file(upload())
        .with(user("customer@example.com").roles("CUSTOMER"))
        .with(csrf()))
      .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void shouldRejectAdministratorWithoutCsrf() throws Exception {
    mockMvc.perform(multipart(URL)
        .file(upload())
        .with(user("admin@example.com").roles("ADMIN")))
      .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void shouldReturnReadableErrorForInvalidPackage() throws Exception {
    when(service.upload(eq(PRODUCT_ID), any(MultipartFile.class)))
      .thenThrow(new InvalidDigitalProductPackageException(
        "Unsupported archive entry"
      ));

    mockMvc.perform(multipart(URL)
        .file(upload())
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf()))
      .andExpect(status().isBadRequest())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.code").value("DIGITAL_PACKAGE_INVALID"));
  }

  @Test
  void shouldRejectRequestWithoutFile() throws Exception {
    mockMvc.perform(multipart(URL)
        .with(user("admin@example.com").roles("ADMIN"))
        .with(csrf()))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void shouldReturnPackageHistoryWithoutStorageKeys() throws Exception {
    var first = savedPackage(1, false);
    var second = savedPackage(2, true);

    when(service.findPackages(PRODUCT_ID))
      .thenReturn(List.of(second, first));

    mockMvc.perform(get(URL)
        .with(user("admin@example.com").roles("ADMIN")))
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].packageVersion").value(2))
      .andExpect(jsonPath("$[0].currentPackage").value(true))
      .andExpect(jsonPath("$[0].originalFilename").value("dragon-v2.zip"))
      .andExpect(jsonPath("$[0].storageKey").doesNotExist())
      .andExpect(jsonPath("$[1].packageVersion").value(1))
      .andExpect(jsonPath("$[1].currentPackage").value(false))
      .andExpect(jsonPath("$[1].storageKey").doesNotExist());
  }

  @Test
  void shouldReturnEmptyHistory() throws Exception {
    when(service.findPackages(PRODUCT_ID))
      .thenReturn(List.of());

    mockMvc.perform(get(URL)
        .with(user("admin@example.com").roles("ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void shouldRejectAnonymousHistoryRequest() throws Exception {
    mockMvc.perform(get(URL))
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(service);
  }

  @Test
  void shouldRejectCustomerHistoryRequest() throws Exception {
    mockMvc.perform(get(URL)
        .with(user("customer@example.com").roles("CUSTOMER")))
      .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  private DigitalProductPackage savedPackage(
    int version,
    boolean current
  ) {
    var file = new DigitalProductPackage(
      PRODUCT_ID,
      version,
      "internal-v" + version + ".zip",
      "dragon-v" + version + ".zip",
      1024,
      "a".repeat(64),
      Instant.parse("2026-10-27T10:00:00Z")
    );

    if (current) {
      file.makeCurrent();
    }

    return file;
  }

  private MockMultipartFile upload() {
    return new MockMultipartFile(
      "file",
      "dragon.zip",
      MediaType.APPLICATION_OCTET_STREAM_VALUE,
      new byte[] {1, 2, 3}
    );
  }


}
