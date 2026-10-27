package pl.lilimi.catalog.digital;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.catalog.ProductRepository;
import pl.lilimi.catalog.ProductType;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

@Service
public class AdminDigitalProductPackageService {

  private static final Logger log =
    LoggerFactory.getLogger(AdminDigitalProductPackageService.class);

  private final ProductRepository products;
  private final DigitalProductPackageRepository packages;
  private final DigitalProductPackageValidator validator;
  private final DigitalProductPackageStorage storage;
  private final TransactionTemplate transactions;

  public AdminDigitalProductPackageService(
    ProductRepository products,
    DigitalProductPackageRepository packages,
    DigitalProductPackageValidator validator,
    DigitalProductPackageStorage storage,
    PlatformTransactionManager transactionManager
  ) {
    this.products = products;
    this.packages = packages;
    this.validator = validator;
    this.storage = storage;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public DigitalProductPackage upload(
    String productId,
    MultipartFile file
  ) {
    if (productId == null || productId.isBlank()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Product ID is required"
      );
    }

    ValidatedDigitalProductPackage validated;

    try {
      validated = validator.validate(file);
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Package could not be read",
        exception
      );
    }

    return Objects.requireNonNull(
      transactions.execute(status ->
        saveNewVersion(productId, validated)
      )
    );
  }

  private DigitalProductPackage saveNewVersion(
    String productId,
    ValidatedDigitalProductPackage validated
  ) {
    var product = products.findByIdForUpdate(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getProductType() != ProductType.DIGITAL) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Only digital products can have a package"
      );
    }

    int nextVersion = Math.addExact(
      packages.findLatestVersion(productId),
      1
    );

    String storageKey;

    try {
      storageKey = storage.store(validated.content());
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Package could not be stored",
        exception
      );
    }

    deleteNewFileOnRollback(storageKey);

    packages.findByProductIdAndCurrentPackageTrue(productId)
      .ifPresent(DigitalProductPackage::retire);

    // Persist retirement before inserting another current package.
    packages.flush();

    var next = new DigitalProductPackage(
      productId,
      nextVersion,
      storageKey,
      validated.originalFilename(),
      validated.byteSize(),
      validated.sha256(),
      Instant.now()
    );

    next.makeCurrent();

    return packages.saveAndFlush(next);
  }

  private void deleteNewFileOnRollback(String storageKey) {
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
          if (status != STATUS_ROLLED_BACK) {
            return;
          }

          try {
            storage.delete(storageKey);
          } catch (IOException exception) {
            log.error(
              "Could not delete digital package {} after rollback",
              storageKey,
              exception
            );
          }
        }
      }
    );
  }

  @Transactional(readOnly = true)
  public List<DigitalProductPackage> findPackages(String productId) {
    if (productId == null || productId.isBlank()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Product ID is required"
      );
    }

    var product = products.findById(productId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Product not found"
      ));

    if (product.getProductType() != ProductType.DIGITAL) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Only digital products can have a package"
      );
    }

    return packages.findByProductIdOrderByPackageVersionDesc(productId);
  }
}
