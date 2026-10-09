package pl.lilimi.catalog.admin.api;

import org.springframework.data.domain.Page;
import pl.lilimi.catalog.Product;

import java.util.List;

public record AdminProductPageResponse(
  List<AdminProductSummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static AdminProductPageResponse from(Page<Product> products) {
    return new AdminProductPageResponse(
      products.getContent()
        .stream()
        .map(AdminProductSummaryResponse::from)
        .toList(),
      products.getNumber(),
      products.getSize(),
      products.getTotalElements(),
      products.getTotalPages()
    );
  }
}
