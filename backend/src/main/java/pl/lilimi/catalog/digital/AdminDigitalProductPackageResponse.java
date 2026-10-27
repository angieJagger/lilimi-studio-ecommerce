package pl.lilimi.catalog.digital;

import java.time.Instant;
import java.util.UUID;

public record AdminDigitalProductPackageResponse(
  UUID id,
  String productId,
  int packageVersion,
  String originalFilename,
  long byteSize,
  String sha256,
  boolean currentPackage,
  Instant createdAt
) {

  public static AdminDigitalProductPackageResponse from(
    DigitalProductPackage file
  ) {
    return new AdminDigitalProductPackageResponse(
      file.getId(),
      file.getProductId(),
      file.getPackageVersion(),
      file.getOriginalFilename(),
      file.getByteSize(),
      file.getSha256(),
      file.isCurrentPackage(),
      file.getCreatedAt()
    );
  }
}
