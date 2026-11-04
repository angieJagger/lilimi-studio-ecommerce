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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;

import pl.lilimi.order.application.DigitalOrderDownloadService;

@RestController
@RequestMapping("/api/orders/{orderId}")
public class DigitalOrderDownloadController {

  private final DigitalOrderDownloadService downloads;

  public DigitalOrderDownloadController(
    DigitalOrderDownloadService downloads
  ) {
    this.downloads = downloads;
  }

  @Operation(
    summary = "Download a purchased ZIP file",
    description = """
    Downloads the ZIP file assigned to an item in a paid order.
    The caller must own the order or use the guest session associated with it.
    """
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "ZIP file returned as an attachment",
      content = @Content(
        mediaType = "application/zip",
        schema = @Schema(type = "string", format = "binary")
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "Order or downloadable item not found, or order access denied",
      content = @Content
    ),
    @ApiResponse(
      responseCode = "409",
      description = "Order is cancelled or payment is not confirmed",
      content = @Content
    ),
    @ApiResponse(
      responseCode = "503",
      description = "ZIP file is temporarily unavailable from storage",
      content = @Content
    )
  })
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

  @Operation(
    summary = "List downloadable files for an order",
    description = """
    Lists the digital files assigned to items in a paid order.
    The caller must own the order or use the guest session associated with it.
    Use the item ID from the response to download the corresponding ZIP file.
    """
  )
  @ApiResponses({
    @ApiResponse(
      responseCode = "200",
      description = "List of downloadable files"
    ),
    @ApiResponse(
      responseCode = "404",
      description = "Order not found or order access denied",
      content = @Content
    ),
    @ApiResponse(
      responseCode = "409",
      description = "Order is cancelled or payment is not confirmed",
      content = @Content
    )
  })
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
