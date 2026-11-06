package pl.lilimi.inquiry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import pl.lilimi.inquiry.persistence.InquiryAttachmentRepository;
import pl.lilimi.inquiry.storage.InquiryAttachmentStorage;

import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class ProjectInquiryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProjectInquiryRepository inquiries;

  @Autowired
  private InquiryAttachmentRepository attachments;

  @MockitoBean
  private InquiryAttachmentStorage attachmentStorage;

  private String validRequest() {
    return """
      {
        "language": "pl",
        "name": "Anna Kowalska",
        "email": "anna@example.com",
        "projectType": "website",
        "description": "Potrzebuję strony dla swojej pracowni.",
        "inspirationUrl": "https://example.com/inspiration"
      }
      """;
  }

  @Test
  void shouldCreateInquiryWithoutLogin() throws Exception {
    long initialCount = inquiries.count();

    var result = mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content(validRequest())
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").isString())
      .andExpect(jsonPath("$.createdAt").isString())
      .andExpect(jsonPath("$.status").value("new"))
      .andExpect(jsonPath("$.email").doesNotExist())
      .andExpect(jsonPath("$.description").doesNotExist())
      .andReturn();

    String id = JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString())
      .get("id")
      .asText();

    var saved = inquiries.findById(UUID.fromString(id)).orElseThrow();

    assertThat(inquiries.count()).isEqualTo(initialCount + 1);
    assertThat(saved.getName()).isEqualTo("Anna Kowalska");
    assertThat(saved.getEmail()).isEqualTo("anna@example.com");
    assertThat(saved.getProjectType()).isEqualTo("website");
    assertThat(saved.getDescription())
      .isEqualTo("Potrzebuję strony dla swojej pracowni.");
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
      post("/api/project-inquiries")
        .contentType("application/json")
        .content(validRequest())
    ).andExpect(status().isForbidden());

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectInvalidEmailWithoutSavingInquiry() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content(validRequest().replace(
            "anna@example.com",
            "invalid-email"
          ))
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INQUIRY_VALIDATION_FAILED"))
      .andExpect(jsonPath("$.fields[0]").value("email"));

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectUnavailableProductWithoutSavingInquiry() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content("""
          {
            "language": "pl",
            "name": "Anna Kowalska",
            "email": "anna@example.com",
            "projectType": "embroideredProduct",
            "description": "Proszę o wycenę personalizacji.",
            "productId": "missing-product"
          }
          """)
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("INQUIRY_PRODUCT_UNAVAILABLE"))
      .andExpect(jsonPath("$.productId").value("missing-product"));

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldRejectUnreadableRequestWithoutSavingInquiry() throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content("{ invalid json")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INQUIRY_REQUEST_INVALID"));

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }
  @ParameterizedTest
  @ValueSource(strings = {
    "uxUi",
    "website",
    "wordpress",
    "graphicDesign",
    "digitizing"
  })
  void shouldSaveServiceTypeAndReturnItToAdmin(String projectType)
    throws Exception {
    long initialCount = inquiries.count();

    var result = mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content(validRequest().replace(
            "\"projectType\": \"website\"",
            "\"projectType\": \"" + projectType + "\""
          ))
      )
      .andExpect(status().isCreated())
      .andReturn();

    String id = JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString())
      .get("id")
      .asText();

    var saved = inquiries.findById(UUID.fromString(id))
      .orElseThrow();

    assertThat(inquiries.count()).isEqualTo(initialCount + 1);
    assertThat(saved.getProjectType()).isEqualTo(projectType);

    mockMvc.perform(
        get("/api/admin/project-inquiries/{id}", id)
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.projectType").value(projectType));
  }

  @Test
  void shouldRejectUnknownServiceTypeWithoutSavingInquiry()
    throws Exception {
    long initialCount = inquiries.count();

    mockMvc.perform(
        post("/api/project-inquiries")
          .with(csrf())
          .contentType("application/json")
          .content(validRequest().replace(
            "\"projectType\": \"website\"",
            "\"projectType\": \"unknown-service\""
          ))
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(
        "INQUIRY_VALIDATION_FAILED"
      ));

    assertThat(inquiries.count()).isEqualTo(initialCount);
  }

  @Test
  void shouldCreateInquiryWithPngAttachmentWithoutLogin() throws Exception {
    var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();

    ImageIO.write(image, "png", output);
    image.flush();

    var requestPart = new MockMultipartFile(
      "request",
      "",
      "application/json",
      validRequest().getBytes(StandardCharsets.UTF_8)
    );

    var filePart = new MockMultipartFile(
      "files",
      "inspiration.png",
      "image/png",
      output.toByteArray()
    );

    String storageKey = "00000000-0000-0000-0000-000000000004.bin";

    when(attachmentStorage.store(any(byte[].class)))
      .thenReturn(storageKey);

    var result = mockMvc.perform(
        multipart("/api/project-inquiries")
          .file(requestPart)
          .file(filePart)
          .with(csrf())
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").isString())
      .andReturn();

    String id = JsonMapper.builder().build()
      .readTree(result.getResponse().getContentAsString())
      .get("id")
      .asText();

    var saved = attachments
      .findAllByInquiryIdOrderByCreatedAtAscIdAsc(UUID.fromString(id));

    assertThat(saved).hasSize(1);
    assertThat(saved.getFirst().getOriginalFilename())
      .isEqualTo("inspiration.png");
    assertThat(saved.getFirst().getContentType())
      .isEqualTo("image/png");
    assertThat(saved.getFirst().getByteSize())
      .isEqualTo(output.size());
    assertThat(saved.getFirst().getStorageKey())
      .isEqualTo(storageKey);
  }

  @Test
  void shouldRejectMultipartInquiryWithoutCsrfToken() throws Exception {
    long initialInquiryCount = inquiries.count();
    long initialAttachmentCount = attachments.count();

    var requestPart = new MockMultipartFile(
      "request",
      "",
      "application/json",
      validRequest().getBytes(StandardCharsets.UTF_8)
    );

    var filePart = new MockMultipartFile(
      "files",
      "inspiration.png",
      "image/png",
      new byte[] { 1, 2, 3 }
    );

    mockMvc.perform(
        multipart("/api/project-inquiries")
          .file(requestPart)
          .file(filePart)
      )
      .andExpect(status().isForbidden());

    assertThat(inquiries.count()).isEqualTo(initialInquiryCount);
    assertThat(attachments.count()).isEqualTo(initialAttachmentCount);

    verifyNoInteractions(attachmentStorage);
  }
}
