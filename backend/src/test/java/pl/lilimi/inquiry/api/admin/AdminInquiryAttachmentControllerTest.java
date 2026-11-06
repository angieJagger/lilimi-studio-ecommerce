package pl.lilimi.inquiry.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.inquiry.application.admin.AdminInquiryAttachmentService;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class AdminInquiryAttachmentControllerTest {

  private static final UUID INQUIRY_ID = UUID.randomUUID();
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminInquiryAttachmentService attachments;

  @Test
  void shouldDownloadAttachmentForAdmin() throws Exception {
    byte[] content = { 1, 2, 3 };

    when(attachments.download(INQUIRY_ID, ATTACHMENT_ID))
      .thenReturn(new AdminInquiryAttachmentService.Download(
        new ByteArrayResource(content),
        "logo.png",
        "image/png",
        content.length
      ));

    mockMvc.perform(
        get(downloadUrl())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(content().contentType("image/png"))
      .andExpect(content().bytes(content))
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(header().string("X-Content-Type-Options", "nosniff"))
      .andExpect(header().string(
        "Content-Disposition",
        org.hamcrest.Matchers.startsWith("attachment;")
      ));
  }

  @Test
  void shouldRejectAnonymousDownload() throws Exception {
    mockMvc.perform(get(downloadUrl()))
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(attachments);
  }

  @Test
  void shouldRejectCustomerDownload() throws Exception {
    mockMvc.perform(
        get(downloadUrl())
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());

    verifyNoInteractions(attachments);
  }

  @Test
  void shouldRejectAnonymousAttachmentListing() throws Exception {
    mockMvc.perform(
        get("/api/admin/project-inquiries/{id}/attachments", INQUIRY_ID)
      )
      .andExpect(status().isUnauthorized());

    verifyNoInteractions(attachments);
  }

  @Test
  void shouldRejectCustomerAttachmentListing() throws Exception {
    mockMvc.perform(
        get("/api/admin/project-inquiries/{id}/attachments", INQUIRY_ID)
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());

    verifyNoInteractions(attachments);
  }

  private String downloadUrl() {
    return "/api/admin/project-inquiries/"
      + INQUIRY_ID
      + "/attachments/"
      + ATTACHMENT_ID
      + "/download";
  }
}
