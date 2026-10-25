package pl.lilimi.catalog.media;

import org.springframework.core.io.Resource;

public record ProductImageContent(
  Resource resource,
  String contentType,
  long byteSize
) {
}
