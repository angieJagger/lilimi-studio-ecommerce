package pl.lilimi.order.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.order.application.DigitalOrderDownloadService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/orders/{orderId}")
public class DigitalOrderDownloadController {

  private final DigitalOrderDownloadService downloads;

  public DigitalOrderDownloadController(
    DigitalOrderDownloadService downloads
  ) {
    this.downloads = downloads;
  }

  @GetMapping("/items/{itemId}/download")
  public ResponseEntity<Resource> download(
    @PathVariable("orderId") UUID orderId,
    @PathVariable("itemId") UUID itemId,
    Authentication authentication,
    HttpServletRequest request
  ) {
    var download = downloads.download(
      orderId,
      itemId,
      authentication,
      request.getSession(false)
    );

    var disposition = ContentDisposition.attachment()
      .filename(download.filename(), StandardCharsets.UTF_8)
      .build();

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("application/zip"))
      .contentLength(download.byteSize())
      .cacheControl(CacheControl.noStore())
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        disposition.toString()
      )
      .body(download.resource());
  }

  @GetMapping("/downloads")
  public ResponseEntity<List<DigitalOrderDownloadResponse>> findDownloads(
    @PathVariable("orderId") UUID orderId,
    Authentication authentication,
    HttpServletRequest request
  ) {
    var response = downloads.findDownloads(
      orderId,
      authentication,
      request.getSession(false)
    );

    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(response);
  }
}
