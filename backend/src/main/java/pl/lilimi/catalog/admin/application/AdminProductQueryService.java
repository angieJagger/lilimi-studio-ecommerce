package pl.lilimi.catalog.admin.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.catalog.Product;
import pl.lilimi.catalog.ProductRepository;

@Service
public class AdminProductQueryService {

  private final ProductRepository repository;

  public AdminProductQueryService(ProductRepository repository) {
    this.repository = repository;
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
}
