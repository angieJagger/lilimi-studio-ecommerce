package pl.lilimi.catalog.admin.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductTranslationRepository;
import pl.lilimi.catalog.admin.api.AdminProductDetailsResponse;

import java.util.List;

@Service
public class AdminProductQueryService {

  private final ProductRepository repository;
  private final ProductTranslationRepository translations;

  public AdminProductQueryService(
    ProductRepository repository,
    ProductTranslationRepository translations
  ) {
    this.repository = repository;
    this.translations = translations;
  }

  @Transactional(readOnly = true)
  public Page<Product> findProducts(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException(
        "Page must be non-negative and size must be between 1 and 100"
      );
    }

    var pageable = PageRequest.of(
      page,
      size,
      Sort.by(Sort.Direction.ASC, "id")
    );

    return repository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public AdminProductDetailsResponse findProduct(String id) {
    var product = repository.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    var productTranslations =
      translations.findAllByIdProductIdIn(List.of(id));

    return AdminProductDetailsResponse.from(
      product,
      productTranslations
    );
  }
}
