package pl.lilimi.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.catalog.media.ProductImageStorage;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
  "lilimi.payments.stripe.enabled=false",
  "lilimi.mail.order-confirmation.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductImageContentControllerTest {

  private static final String PRODUCT_ID = "pattern-001";
  private static final String STORAGE_KEY =
    "00000000-0000-0000-0000-000000000001.png";
  private static final String URL =
    "/api/admin/products/{productId}/images/{imageId}/content";

  private static final byte[] FILE_CONTENT = { 1, 2, 3, 4 };

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductImageRepository images;

  @MockitoBean
  private ProductImageStorage storage;

  private UUID imageId;

  @BeforeEach
  void setUp() throws Exception {
    clearInvocations(storage);

    imageId = UUID.randomUUID();

    images.saveAndFlush(new ProductImage(
      imageId,
      PRODUCT_ID,
      STORAGE_KEY,
      "image/png",
      FILE_CONTENT.length,
      4,
      3,
      0,
      true,
      "Haft kwiatowy",
      "Floral embroidery"
    ));

    when(storage.load(STORAGE_KEY))
      .thenReturn(new ByteArrayResource(FILE_CONTENT));
  }

  @Test
  void shouldReturnImageContentForAdministrator() throws Exception {
    mockMvc.perform(
        get(URL, PRODUCT_ID, imageId)
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(content().contentType(MediaType.IMAGE_PNG))
      .andExpect(content().bytes(FILE_CONTENT))
      .andExpect(header().string(
        "Content-Length",
        String.valueOf(FILE_CONTENT.length)
      ))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(header().string(
        "X-Content-Type-Options",
        "nosniff"
      ));

    verify(storage).load(STORAGE_KEY);
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(get(URL, PRODUCT_ID, imageId))
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        get(URL, PRODUCT_ID, imageId)
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldRejectImageBelongingToAnotherProduct() throws Exception {
    mockMvc.perform(
        get(URL, "pattern-002", imageId)
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isNotFound());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldReturnNotFoundForMissingImage() throws Exception {
    mockMvc.perform(
        get(URL, PRODUCT_ID, UUID.randomUUID())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isNotFound());

    verifyNoInteractions(storage);
  }

  @Test
  void shouldReturnNotFoundWhenStoredFileIsMissing() throws Exception {
    when(storage.load(STORAGE_KEY))
      .thenThrow(new NoSuchFileException(STORAGE_KEY));

    mockMvc.perform(
        get(URL, PRODUCT_ID, imageId)
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnServerErrorWhenStorageFails() throws Exception {
    when(storage.load(STORAGE_KEY))
      .thenThrow(new IOException("Storage unavailable"));

    mockMvc.perform(
        get(URL, PRODUCT_ID, imageId)
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isInternalServerError());
  }
}
