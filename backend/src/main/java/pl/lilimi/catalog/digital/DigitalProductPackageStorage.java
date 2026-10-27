package pl.lilimi.catalog.digital;

import org.springframework.core.io.Resource;

import java.io.IOException;

public interface DigitalProductPackageStorage {

  String store(byte[] content) throws IOException;

  Resource load(String storageKey) throws IOException;

  void delete(String storageKey) throws IOException;
}
