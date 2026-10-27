package pl.lilimi.catalog.digital;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products/{productId}/digital-packages")
public class AdminDigitalProductPackageController {

  private final AdminDigitalProductPackageService service;

  public AdminDigitalProductPackageController(
    AdminDigitalProductPackageService service
  ) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminDigitalProductPackageResponse> upload(
    @PathVariable("productId") String productId,
    @RequestPart("file") MultipartFile file
  ) {
    var saved = service.upload(productId, file);

    return ResponseEntity.status(HttpStatus.CREATED)
      .cacheControl(CacheControl.noStore())
      .body(AdminDigitalProductPackageResponse.from(saved));
  }

  @ExceptionHandler(InvalidDigitalProductPackageException.class)
  public ResponseEntity<ProblemDetail> handleInvalidPackage(
    InvalidDigitalProductPackageException exception
  ) {
    var problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "The uploaded ZIP package is invalid."
    );

    problem.setTitle("Invalid digital product package");
    problem.setProperty("code", "DIGITAL_PACKAGE_INVALID");

    return ResponseEntity.badRequest()
      .cacheControl(CacheControl.noStore())
      .body(problem);
  }

  @GetMapping
  public ResponseEntity<List<AdminDigitalProductPackageResponse>> findPackages(
    @PathVariable("productId") String productId
  ) {
    var response = service.findPackages(productId)
      .stream()
      .map(AdminDigitalProductPackageResponse::from)
      .toList();

    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(response);
  }
}
