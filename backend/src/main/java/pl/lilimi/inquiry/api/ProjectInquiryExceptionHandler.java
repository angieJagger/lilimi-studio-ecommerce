package pl.lilimi.inquiry.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import pl.lilimi.inquiry.application.InvalidInquiryAttachmentException;
import pl.lilimi.inquiry.application.InquiryProductUnavailableException;

@RestControllerAdvice(
  assignableTypes = ProjectInquiryController.class
)
public class ProjectInquiryExceptionHandler {

  @ExceptionHandler(InquiryProductUnavailableException.class)
  public ProblemDetail handleUnavailableProduct(
    InquiryProductUnavailableException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      "The referenced product is unavailable"
    );

    problem.setTitle("Inquiry product unavailable");
    problem.setProperty("code", "INQUIRY_PRODUCT_UNAVAILABLE");
    problem.setProperty("productId", exception.getProductId());

    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
    MethodArgumentNotValidException exception
  ) {
    var fields = exception.getBindingResult()
      .getFieldErrors()
      .stream()
      .map(error -> error.getField())
      .distinct()
      .sorted()
      .toList();

    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "Inquiry data failed validation"
    );

    problem.setTitle("Invalid inquiry data");
    problem.setProperty("code", "INQUIRY_VALIDATION_FAILED");
    problem.setProperty("fields", fields);

    return problem;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableRequest(
    HttpMessageNotReadableException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "Inquiry request could not be read"
    );

    problem.setTitle("Invalid inquiry request");
    problem.setProperty("code", "INQUIRY_REQUEST_INVALID");

    return problem;
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ProblemDetail handleUploadTooLarge(
    MaxUploadSizeExceededException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.PAYLOAD_TOO_LARGE,
      "An attachment or the multipart request exceeds the upload limit"
    );

    problem.setTitle("Upload limit exceeded");
    problem.setProperty("code", "INQUIRY_UPLOAD_TOO_LARGE");

    return problem;
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ProblemDetail handleMissingRequestPart(
    MissingServletRequestPartException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "A required multipart request part is missing"
    );

    problem.setTitle("Incomplete inquiry request");
    problem.setProperty("code", "INQUIRY_REQUEST_PART_MISSING");
    problem.setProperty("part", exception.getRequestPartName());

    return problem;
  }

  @ExceptionHandler(InvalidInquiryAttachmentException.class)
  public ProblemDetail handleInvalidAttachment(
    InvalidInquiryAttachmentException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "An attachment could not be accepted"
    );

    problem.setTitle("Invalid inquiry attachment");
    problem.setProperty("code", "INQUIRY_ATTACHMENT_INVALID");

    return problem;
  }
}
