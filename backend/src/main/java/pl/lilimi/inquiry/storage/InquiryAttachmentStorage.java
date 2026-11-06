package pl.lilimi.inquiry.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface InquiryAttachmentStorage {

  String store(byte[] content) throws IOException;

  Resource load(String storageKey) throws IOException;

  void delete(String storageKey) throws IOException;

  List<String> findStoredBefore(Instant cutoff) throws IOException;
}
