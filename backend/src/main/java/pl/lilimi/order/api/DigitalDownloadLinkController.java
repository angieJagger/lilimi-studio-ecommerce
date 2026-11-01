package pl.lilimi.order.api;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.lilimi.order.application.DigitalOrderDownloadService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/digital-downloads")
public class DigitalDownloadLinkController {

  private final DigitalOrderDownloadService downloads;

  public DigitalDownloadLinkController(
    DigitalOrderDownloadService downloads
  ) {
    this.downloads = downloads;
  }

  @GetMapping
  public ResponseEntity<List<DigitalOrderDownloadResponse>> list(
    @RequestHeader(
      name = "X-Digital-Download-Token",
      required = false
    )
    String rawToken
  ) {
    var files = downloads.findDownloadsWithToken(rawToken);

    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .header("Referrer-Policy", "no-referrer")
      .body(files);
  }

  @GetMapping("/items/{itemId}/download")
  public ResponseEntity<Resource> download(
    @PathVariable("itemId") UUID itemId,
    @RequestHeader(
      name = "X-Digital-Download-Token",
      required = false
    )
    String rawToken
  ) {
    var file = downloads.downloadWithToken(rawToken, itemId);

    var disposition = ContentDisposition.attachment()
      .filename(file.filename(), StandardCharsets.UTF_8)
      .build();

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("application/zip"))
      .contentLength(file.byteSize())
      .cacheControl(CacheControl.noStore())
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        disposition.toString()
      )
      .header("Referrer-Policy", "no-referrer")
      .body(file.resource());
  }
}
