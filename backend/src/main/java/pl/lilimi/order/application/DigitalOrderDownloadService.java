package pl.lilimi.order.application;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.order.api.DigitalOrderDownloadResponse;
import pl.lilimi.catalog.digital.DigitalProductPackageRepository;
import pl.lilimi.catalog.digital.DigitalProductPackageStorage;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.access.OrderPaymentAccessService;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.io.IOException;
import java.util.UUID;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DigitalOrderDownloadService {

  private final OrderPaymentAccessService access;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final PaymentAttemptRepository payments;
  private final DigitalProductPackageRepository packages;
  private final DigitalProductPackageStorage storage;

  public DigitalOrderDownloadService(
    OrderPaymentAccessService access,
    OrderRepository orders,
    OrderItemRepository items,
    PaymentAttemptRepository payments,
    DigitalProductPackageRepository packages,
    DigitalProductPackageStorage storage
  ) {
    this.access = access;
    this.orders = orders;
    this.items = items;
    this.payments = payments;
    this.packages = packages;
    this.storage = storage;
  }

  public DigitalOrderDownload download(
    UUID orderId,
    UUID itemId,
    Authentication authentication,
    HttpSession session
  ) {
    requirePaidOrder(orderId, authentication, session);

    var item = items.findByIdAndOrder_Id(itemId, orderId)
      .orElseThrow(DigitalOrderDownloadService::notFound);

    if (
      !"digital".equals(item.getKind()) ||
        item.getDigitalPackageId() == null
    ) {
      throw notFound();
    }

    var digitalPackage = packages
      .findById(item.getDigitalPackageId())
      .orElseThrow(DigitalOrderDownloadService::notFound);

    if (!item.getProductId().equals(digitalPackage.getProductId())) {
      throw notFound();
    }

    try {
      var resource = storage.load(digitalPackage.getStorageKey());

      return new DigitalOrderDownload(
        resource,
        digitalPackage.getOriginalFilename(),
        digitalPackage.getByteSize()
      );
    } catch (IOException exception) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "DIGITAL_DOWNLOAD_UNAVAILABLE",
        exception
      );
    }
  }

  public List<DigitalOrderDownloadResponse> findDownloads(
    UUID orderId,
    Authentication authentication,
    HttpSession session
  ) {
    requirePaidOrder(orderId, authentication, session);

    return items.findByOrder_IdOrderByPositionAsc(orderId)
      .stream()
      .filter(item ->
        "digital".equals(item.getKind()) &&
          item.getDigitalPackageId() != null
      )
      .map(item -> {
        var digitalPackage = packages
          .findById(item.getDigitalPackageId())
          .orElseThrow(DigitalOrderDownloadService::notFound);

        if (!item.getProductId().equals(digitalPackage.getProductId())) {
          throw notFound();
        }

        return new DigitalOrderDownloadResponse(
          item.getId(),
          item.getProductName(),
          digitalPackage.getOriginalFilename(),
          digitalPackage.getPackageVersion(),
          digitalPackage.getByteSize()
        );
      })
      .toList();
  }

  private void requirePaidOrder(
    UUID orderId,
    Authentication authentication,
    HttpSession session
  ) {
    access.requireAccess(orderId, authentication, session);

    var order = orders.findById(orderId)
      .orElseThrow(DigitalOrderDownloadService::notFound);

    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_CANCELLED"
      );
    }

    if (!payments.existsByOrderIdAndStatus(
      orderId,
      PaymentAttemptStatus.SUCCEEDED
    )) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "ORDER_NOT_PAID"
      );
    }
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "DIGITAL_DOWNLOAD_NOT_FOUND"
    );
  }
}
