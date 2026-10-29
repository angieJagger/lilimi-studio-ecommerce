package pl.lilimi.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.catalog.digital.DigitalProductPackage;
import pl.lilimi.catalog.digital.DigitalProductPackageRepository;
import pl.lilimi.catalog.digital.DigitalProductPackageStorage;
import pl.lilimi.order.application.DigitalOrderDownloadService;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderItem;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.access.OrderPaymentAccessService;
import pl.lilimi.payment.domain.PaymentAttemptStatus;
import pl.lilimi.payment.persistence.PaymentAttemptRepository;

import java.util.Optional;
import java.util.UUID;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigitalOrderDownloadServiceTest {

  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID PACKAGE_ID = UUID.randomUUID();

  @Mock
  private OrderPaymentAccessService access;

  @Mock
  private OrderRepository orders;

  @Mock
  private OrderItemRepository items;

  @Mock
  private PaymentAttemptRepository payments;

  @Mock
  private DigitalProductPackageRepository packages;

  @Mock
  private DigitalProductPackageStorage storage;

  @InjectMocks
  private DigitalOrderDownloadService service;

  @Test
  void shouldDownloadPackageAssignedToPurchasedItem() throws Exception {
    prepareOrder(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    var item = mock(OrderItem.class);

    when(item.getKind()).thenReturn("digital");
    when(item.getProductId()).thenReturn("pattern-001");
    when(item.getDigitalPackageId()).thenReturn(PACKAGE_ID);

    when(items.findByIdAndOrder_Id(ITEM_ID, ORDER_ID))
      .thenReturn(Optional.of(item));

    var digitalPackage = mock(DigitalProductPackage.class);

    when(digitalPackage.getProductId()).thenReturn("pattern-001");
    when(digitalPackage.getStorageKey()).thenReturn("purchased.zip");
    when(digitalPackage.getOriginalFilename()).thenReturn("dragon.zip");
    when(digitalPackage.getByteSize()).thenReturn(3L);

    when(packages.findById(PACKAGE_ID))
      .thenReturn(Optional.of(digitalPackage));

    var resource = new ByteArrayResource(new byte[] {1, 2, 3});

    when(storage.load("purchased.zip")).thenReturn(resource);

    var result = service.download(
      ORDER_ID,
      ITEM_ID,
      null,
      null
    );

    assertThat(result.resource()).isSameAs(resource);
    assertThat(result.filename()).isEqualTo("dragon.zip");
    assertThat(result.byteSize()).isEqualTo(3);

    verify(access).requireAccess(ORDER_ID, null, null);
    verify(packages, never())
      .findByProductIdAndCurrentPackageTrue(anyString());
  }

  @Test
  void shouldRejectUnpaidOrderEvenWhenMarkedCompleted() {
    prepareOrder(OrderStatus.COMPLETED);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(false);

    assertThatThrownBy(() ->
      service.download(ORDER_ID, ITEM_ID, null, null)
    )
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode().value()).isEqualTo(409);
          assertThat(exception.getReason()).isEqualTo("ORDER_NOT_PAID");
        }
      );

    verifyNoInteractions(items, packages, storage);
  }

  @Test
  void shouldStopImmediatelyWhenOrderAccessIsDenied() {
    doThrow(new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "ORDER_NOT_FOUND"
    ))
      .when(access)
      .requireAccess(ORDER_ID, null, null);

    assertThatThrownBy(() ->
      service.download(ORDER_ID, ITEM_ID, null, null)
    )
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception ->
          assertThat(exception.getStatusCode().value()).isEqualTo(404)
      );

    verifyNoInteractions(orders, payments, items, packages, storage);
  }

  @Test
  void shouldRejectCancelledOrder() {
    prepareOrder(OrderStatus.CANCELLED);

    assertThatThrownBy(() ->
      service.download(ORDER_ID, ITEM_ID, null, null)
    )
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode().value()).isEqualTo(409);
          assertThat(exception.getReason()).isEqualTo("ORDER_CANCELLED");
        }
      );

    verifyNoInteractions(payments, items, packages, storage);
  }

  @Test
  void shouldRejectItemNotBelongingToOrder() {
    prepareOrder(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    when(items.findByIdAndOrder_Id(ITEM_ID, ORDER_ID))
      .thenReturn(Optional.empty());

    assertThatThrownBy(() ->
      service.download(ORDER_ID, ITEM_ID, null, null)
    )
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception ->
          assertThat(exception.getStatusCode().value()).isEqualTo(404)
      );

    verify(items).findByIdAndOrder_Id(ITEM_ID, ORDER_ID);
    verifyNoInteractions(packages, storage);
  }

  @Test
  void shouldRejectDigitalItemWithoutAssignedPackage() {
    prepareOrder(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    var item = mock(OrderItem.class);

    when(item.getKind()).thenReturn("digital");
    when(items.findByIdAndOrder_Id(ITEM_ID, ORDER_ID))
      .thenReturn(Optional.of(item));

    // Brak przypisania, np. dla zamówienia sprzed migracji.
    assertThatThrownBy(() ->
      service.download(ORDER_ID, ITEM_ID, null, null)
    )
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception ->
          assertThat(exception.getStatusCode().value()).isEqualTo(404)
      );

    verifyNoInteractions(packages, storage);
  }

  @Test
  void shouldRejectPackageAssignedToAnotherProduct() {
    prepareOrder(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    var item = mock(OrderItem.class);

    when(item.getKind()).thenReturn("digital");
    when(item.getProductId()).thenReturn("pattern-001");
    when(item.getDigitalPackageId()).thenReturn(PACKAGE_ID);

    when(items.findByIdAndOrder_Id(ITEM_ID, ORDER_ID))
      .thenReturn(Optional.of(item));

    var digitalPackage = mock(DigitalProductPackage.class);

    when(digitalPackage.getProductId()).thenReturn("pattern-002");
    when(packages.findById(PACKAGE_ID))
      .thenReturn(Optional.of(digitalPackage));

    assertThatThrownBy(() ->
      service.download(ORDER_ID, ITEM_ID, null, null)
    )
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception ->
          assertThat(exception.getStatusCode().value()).isEqualTo(404)
      );

    verifyNoInteractions(storage);
  }

  @Test
  void shouldReturnUnavailableWhenStoredFileCannotBeRead() throws Exception {
    prepareOrder(OrderStatus.NEW);

    when(payments.existsByOrderIdAndStatus(
      ORDER_ID,
      PaymentAttemptStatus.SUCCEEDED
    )).thenReturn(true);

    var item = mock(OrderItem.class);

    when(item.getKind()).thenReturn("digital");
    when(item.getProductId()).thenReturn("pattern-001");
    when(item.getDigitalPackageId()).thenReturn(PACKAGE_ID);

    when(items.findByIdAndOrder_Id(ITEM_ID, ORDER_ID))
      .thenReturn(Optional.of(item));

    var digitalPackage = mock(DigitalProductPackage.class);

    when(digitalPackage.getProductId()).thenReturn("pattern-001");
    when(digitalPackage.getStorageKey()).thenReturn("missing.zip");

    when(packages.findById(PACKAGE_ID))
      .thenReturn(Optional.of(digitalPackage));

    when(storage.load("missing.zip"))
      .thenThrow(new IOException("Simulated storage failure"));

    assertThatThrownBy(() ->
      service.download(ORDER_ID, ITEM_ID, null, null)
    )
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode().value()).isEqualTo(503);
          assertThat(exception.getReason())
            .isEqualTo("DIGITAL_DOWNLOAD_UNAVAILABLE");
        }
      );
  }

  private void prepareOrder(OrderStatus status) {
    var order = mock(Order.class);

    when(order.getStatus()).thenReturn(status);
    when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
  }
}
