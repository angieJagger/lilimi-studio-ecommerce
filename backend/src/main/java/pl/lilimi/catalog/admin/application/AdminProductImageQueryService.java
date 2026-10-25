package pl.lilimi.catalog.admin.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.ProductImageRepository;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.admin.api.AdminProductImagesResponse;

@Service
public class AdminProductImageQueryService {

  private final ProductRepository products;
  private final ProductImageRepository images;

  public AdminProductImageQueryService(
    ProductRepository products,
    ProductImageRepository images
  ) {
    this.products = products;
    this.images = images;
  }

  @Transactional(
    readOnly = true,
    isolation = Isolation.REPEATABLE_READ
  )
  public AdminProductImagesResponse findImages(String productId) {
    var product = products.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    var productImages =
      images.findAllByProductIdOrderBySortOrderAscIdAsc(productId);

    return AdminProductImagesResponse.from(
      product,
      productImages
    );
  }
}
