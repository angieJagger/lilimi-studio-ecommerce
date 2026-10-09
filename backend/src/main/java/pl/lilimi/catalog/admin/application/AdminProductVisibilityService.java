package pl.lilimi.catalog.admin.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductSummaryResponse;
import pl.lilimi.catalog.admin.api.ChangeProductVisibilityRequest;

@Service
@Validated
public class AdminProductVisibilityService {

  private final ProductRepository repository;

  public AdminProductVisibilityService(
    ProductRepository repository
  ) {
    this.repository = repository;
  }

  @Transactional
  public AdminProductSummaryResponse changeVisibility(
    @NotBlank String id,
    @NotNull @Valid ChangeProductVisibilityRequest request
  ) {
    var product = repository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getVersion() != request.expectedVersion()) {
      throw new ProductVersionConflictException();
    }

    product.changeVisibility(request.active());

    repository.flush();

    return AdminProductSummaryResponse.from(product);
  }
}
