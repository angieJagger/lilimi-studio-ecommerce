package pl.lilimi.order.api;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.lilimi.order.application.ResendDigitalDownloadLinkService;

@RestController
@RequestMapping("/api/digital-downloads/resend")
public class ResendDigitalDownloadLinkController {

  private final ResendDigitalDownloadLinkService service;

  public ResendDigitalDownloadLinkController(
    ResendDigitalDownloadLinkService service
  ) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<Void> resend(
    @Valid @RequestBody ResendDigitalDownloadLinkRequest request
  ) {
    service.request(request.orderId(), request.email());

    return ResponseEntity.accepted()
      .cacheControl(CacheControl.noStore())
      .build();
  }
}
