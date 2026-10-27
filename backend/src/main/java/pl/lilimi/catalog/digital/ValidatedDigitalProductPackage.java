package pl.lilimi.catalog.digital;

import java.util.Objects;

public record ValidatedDigitalProductPackage(
  byte[] content,
  String originalFilename,
  String sha256
) {

  public ValidatedDigitalProductPackage {
    Objects.requireNonNull(content, "Content is required");
    Objects.requireNonNull(originalFilename, "Filename is required");
    Objects.requireNonNull(sha256, "Checksum is required");

    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  public long byteSize() {
    return content.length;
  }
}
