package pl.lilimi.catalog;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.catalog.media.PublicProductImageService;

import java.util.UUID;

@RestController
@RequestMapping("/api/products/{slug}/images")
public class ProductImageController {
  private final PublicProductImageService images;

  public ProductImageController(PublicProductImageService images) { this.images = images; }

  @GetMapping("/{imageId}/content")
  public ResponseEntity<Resource> content(@PathVariable String slug, @PathVariable UUID imageId) {
    var image = images.load(slug, imageId);
    // Revalidate on each visit so hiding a product also hides previously used image URLs.
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
      .contentLength(image.byteSize()).cacheControl(CacheControl.noCache())
      .eTag(image.etag()).header("X-Content-Type-Options", "nosniff").body(image.resource());
  }
}
