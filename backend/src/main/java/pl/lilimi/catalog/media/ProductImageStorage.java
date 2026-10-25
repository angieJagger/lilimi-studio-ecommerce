package pl.lilimi.catalog.media;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface ProductImageStorage {

  String store(byte[] content, String extension) throws IOException;

  Resource load(String storageKey) throws IOException;

  void delete(String storageKey) throws IOException;
}
