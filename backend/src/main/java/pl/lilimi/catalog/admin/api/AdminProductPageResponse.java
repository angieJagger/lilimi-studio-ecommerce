package pl.lilimi.catalog.admin.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminProductPageResponse(
  List<AdminProductSummaryResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public static AdminProductPageResponse from(Page<AdminProductSummaryResponse> products) {
    return new AdminProductPageResponse(
      products.getContent(),
      products.getNumber(),
      products.getSize(),
      products.getTotalElements(),
      products.getTotalPages()
    );
  }
}
