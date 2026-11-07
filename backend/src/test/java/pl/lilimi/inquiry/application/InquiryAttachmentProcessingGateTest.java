package pl.lilimi.inquiry.application;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryAttachmentProcessingGateTest {

  private final InquiryAttachmentProcessingGate gate =
    new InquiryAttachmentProcessingGate();

  @Test
  void shouldRejectConcurrentProcessingAndReleaseAfterCompletion()
    throws Exception {

    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);

    try (var executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> gate.execute(() -> {
        started.countDown();

        try {
          if (!finish.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test operation timed out");
          }
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(exception);
        }

        return "first";
      }));

      try {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> gate.execute(() -> "second"))
          .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode())
              .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
          );
      } finally {
        finish.countDown();
      }

      assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
    }

    assertThat(gate.execute(() -> "next")).isEqualTo("next");
  }

  @Test
  void shouldReleasePermitAfterFailure() {
    var failure = new IllegalArgumentException("Invalid attachment");

    assertThatThrownBy(() -> gate.execute(() -> {
      throw failure;
    })).isSameAs(failure);

    assertThat(gate.execute(() -> "next")).isEqualTo("next");
  }
}
