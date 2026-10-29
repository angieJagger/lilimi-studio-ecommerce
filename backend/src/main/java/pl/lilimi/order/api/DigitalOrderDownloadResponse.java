package pl.lilimi.order.api;

import java.util.UUID;

public record DigitalOrderDownloadResponse(
  UUID itemId,
  String productName,
  String filename,
  int packageVersion,
  long byteSize
) {
}
