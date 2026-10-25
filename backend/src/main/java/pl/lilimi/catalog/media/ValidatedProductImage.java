package pl.lilimi.catalog.media;

public record ValidatedProductImage(
  byte[] content,
  String extension,
  String contentType,
  int width,
  int height
) {
}
