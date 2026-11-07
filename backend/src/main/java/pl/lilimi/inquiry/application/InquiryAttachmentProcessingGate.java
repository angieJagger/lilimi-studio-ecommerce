package pl.lilimi.inquiry.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class InquiryAttachmentProcessingGate {

  private final Semaphore permits = new Semaphore(1);

  public <T> T execute(Supplier<T> operation) {
    if (!permits.tryAcquire()) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Attachment processing is busy. Please try again shortly."
      );
    }

    try {
      return operation.get();
    } finally {
      permits.release();
    }
  }
}
