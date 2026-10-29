package pl.lilimi.order.application;

import org.springframework.core.io.Resource;

public record DigitalOrderDownload(
  Resource resource,
  String filename,
  long byteSize
) {
}
