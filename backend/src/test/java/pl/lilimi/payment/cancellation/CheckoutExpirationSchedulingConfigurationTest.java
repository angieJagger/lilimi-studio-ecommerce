package pl.lilimi.payment.cancellation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutExpirationSchedulingConfigurationTest {

  @Mock
  private CheckoutExpirationProcessor processor;

  private CheckoutExpirationSchedulingConfiguration scheduling;

  @BeforeEach
  void setUp() {
    scheduling = new CheckoutExpirationSchedulingConfiguration(
      processor
    );
  }

  @Test
  void shouldStopWhenQueueIsEmpty() {
    when(processor.processNext()).thenReturn(false);

    scheduling.processQueue();

    verify(processor).processNext();
  }

  @Test
  void shouldStopAfterProcessingAvailableTasks() {
    when(processor.processNext()).thenReturn(true, true, false);

    scheduling.processQueue();

    verify(processor, times(3)).processNext();
  }

  @Test
  void shouldLimitNumberOfTasksPerRun() {
    when(processor.processNext()).thenReturn(true);

    scheduling.processQueue();

    verify(processor, times(10)).processNext();
  }

  @Test
  void shouldAllowNextRunAfterUnexpectedFailure() {
    when(processor.processNext())
      .thenThrow(new IllegalStateException("Database unavailable"))
      .thenReturn(false);

    assertThatCode(() -> scheduling.processQueue())
      .doesNotThrowAnyException();

    scheduling.processQueue();

    verify(processor, times(2)).processNext();
  }
}
