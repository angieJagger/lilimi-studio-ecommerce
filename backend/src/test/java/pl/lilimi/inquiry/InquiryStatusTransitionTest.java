package pl.lilimi.inquiry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.lilimi.inquiry.domain.InquiryStatus;
import pl.lilimi.inquiry.domain.ProjectInquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryStatusTransitionTest {

  @Test
  void shouldStartWithNewStatus() {
    assertThat(createInquiry().getStatus()).isEqualTo("new");
  }

  @ParameterizedTest
  @CsvSource({
    "NEW, IN_PROGRESS",
    "NEW, CLOSED",
    "IN_PROGRESS, ANSWERED",
    "IN_PROGRESS, CLOSED",
    "ANSWERED, IN_PROGRESS",
    "ANSWERED, CLOSED"
  })
  void shouldAllowValidTransitions(
    InquiryStatus initialStatus,
    InquiryStatus nextStatus
  ) {
    var inquiry = inquiryWithStatus(initialStatus);

    inquiry.changeStatus(nextStatus);

    assertThat(inquiry.getStatus())
      .isEqualTo(nextStatus.getValue());
  }

  @ParameterizedTest
  @CsvSource({
    "NEW, ANSWERED",
    "IN_PROGRESS, NEW",
    "ANSWERED, NEW",
    "CLOSED, NEW",
    "CLOSED, IN_PROGRESS",
    "CLOSED, ANSWERED"
  })
  void shouldRejectInvalidTransitions(
    InquiryStatus initialStatus,
    InquiryStatus nextStatus
  ) {
    var inquiry = inquiryWithStatus(initialStatus);

    assertThatThrownBy(() -> inquiry.changeStatus(nextStatus))
      .isInstanceOf(IllegalStateException.class);

    assertThat(inquiry.getStatus())
      .isEqualTo(initialStatus.getValue());
  }

  @ParameterizedTest
  @CsvSource({
    "NEW",
    "IN_PROGRESS",
    "ANSWERED",
    "CLOSED"
  })
  void shouldKeepStatusWhenTheSameStatusIsRequested(
    InquiryStatus status
  ) {
    var inquiry = inquiryWithStatus(status);

    inquiry.changeStatus(status);

    assertThat(inquiry.getStatus()).isEqualTo(status.getValue());
  }

  @Test
  void shouldRejectNullStatus() {
    var inquiry = createInquiry();

    assertThatThrownBy(() -> inquiry.changeStatus(null))
      .isInstanceOf(IllegalArgumentException.class);

    assertThat(inquiry.getStatus()).isEqualTo("new");
  }

  private static ProjectInquiry inquiryWithStatus(
    InquiryStatus status
  ) {
    var inquiry = createInquiry();

    switch (status) {
      case NEW -> {
        // Newly created inquiries already have this status.
      }
      case IN_PROGRESS ->
        inquiry.changeStatus(InquiryStatus.IN_PROGRESS);

      case ANSWERED -> {
        inquiry.changeStatus(InquiryStatus.IN_PROGRESS);
        inquiry.changeStatus(InquiryStatus.ANSWERED);
      }

      case CLOSED ->
        inquiry.changeStatus(InquiryStatus.CLOSED);
    }

    return inquiry;
  }

  private static ProjectInquiry createInquiry() {
    return new ProjectInquiry(
      "pl",
      "Anna Kowalska",
      "anna@example.com",
      "website",
      "Potrzebuję strony dla pracowni.",
      null,
      null
    );
  }
}
