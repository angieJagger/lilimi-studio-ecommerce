package pl.lilimi.catalog.media;

import org.springframework.core.io.Resource;

public record PublicProductImageContent(
  Resource resource, String contentType, long byteSize, String etag
) {}
