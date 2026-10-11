package pl.lilimi.catalog.admin.api;

import org.springframework.data.domain.Page;
import pl.lilimi.catalog.GarmentVariant;

import java.util.List;

public record AdminGarmentVariantPageResponse(
  List<AdminGarmentVariantResponse> items,
  int page,
  int size,
  long totalElements,
  int totalPages
) {

  public AdminGarmentVariantPageResponse {
    items = List.copyOf(items);
  }

  public static AdminGarmentVariantPageResponse from(
    Page<GarmentVariant> variants
  ) {
    return new AdminGarmentVariantPageResponse(
      variants.getContent().stream()
        .map(AdminGarmentVariantResponse::from)
        .toList(),
      variants.getNumber(),
      variants.getSize(),
      variants.getTotalElements(),
      variants.getTotalPages()
    );
  }
}
