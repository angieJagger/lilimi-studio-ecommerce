package pl.lilimi.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.application.CurrentAccountService;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.application.GuestOrderAccessService;
import pl.lilimi.payment.application.OrderPaymentAccessService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentAccessServiceTest {

  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final UUID OWNER_ID = UUID.randomUUID();

  @Mock
  private OrderRepository orders;

  @Mock
  private CurrentAccountService currentAccount;

  @Mock
  private Order order;

  @Mock
  private Authentication authentication;

  private GuestOrderAccessService guestAccess;
  private OrderPaymentAccessService service;

  @BeforeEach
  void setUp() {
    guestAccess = new GuestOrderAccessService();

    service = new OrderPaymentAccessService(
      orders,
      currentAccount,
      guestAccess
    );
  }

  @Test
  void shouldAllowGuestWithGrantInSameSession() {
    existingOrder();

    var session = new MockHttpSession();
    guestAccess.grant(session, ORDER_ID);

    assertThatCode(() ->
      service.requireAccess(ORDER_ID, null, session)
    ).doesNotThrowAnyException();

    verifyNoInteractions(currentAccount);
  }

  @Test
  void shouldRejectGuestWithoutSession() {
    existingOrder();

    assertNotFound(() ->
      service.requireAccess(ORDER_ID, null, null)
    );
  }

  @Test
  void shouldRejectDifferentGuestSession() {
    existingOrder();

    var ownerSession = new MockHttpSession();
    var otherSession = new MockHttpSession();

    guestAccess.grant(ownerSession, ORDER_ID);

    assertNotFound(() ->
      service.requireAccess(ORDER_ID, null, otherSession)
    );

    assertThat(guestAccess.hasAccess(ownerSession, ORDER_ID))
      .isTrue();
  }

  @Test
  void shouldRejectInvalidatedGuestSession() {
    existingOrder();

    var session = new MockHttpSession();

    guestAccess.grant(session, ORDER_ID);
    session.invalidate();

    assertNotFound(() ->
      service.requireAccess(ORDER_ID, null, session)
    );
  }

  @Test
  void shouldAllowSignedInOwner() {
    existingOrder();

    when(order.getCustomerAccountId())
      .thenReturn(OWNER_ID);

    when(currentAccount.findAccountId(authentication))
      .thenReturn(OWNER_ID);

    assertThatCode(() ->
      service.requireAccess(
        ORDER_ID,
        authentication,
        new MockHttpSession()
      )
    ).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectDifferentSignedInAccount() {
    existingOrder();

    when(order.getCustomerAccountId())
      .thenReturn(OWNER_ID);

    when(currentAccount.findAccountId(authentication))
      .thenReturn(UUID.randomUUID());

    assertNotFound(() ->
      service.requireAccess(
        ORDER_ID,
        authentication,
        new MockHttpSession()
      )
    );
  }

  @Test
  void shouldNotUseGuestGrantToOverrideAccountOwnership() {
    existingOrder();

    when(order.getCustomerAccountId())
      .thenReturn(OWNER_ID);

    when(currentAccount.findAccountId(authentication))
      .thenReturn(UUID.randomUUID());

    var session = new MockHttpSession();
    guestAccess.grant(session, ORDER_ID);

    assertNotFound(() ->
      service.requireAccess(
        ORDER_ID,
        authentication,
        session
      )
    );
  }

  @Test
  void shouldReturnSameNotFoundForMissingOrder() {
    when(orders.findById(ORDER_ID))
      .thenReturn(Optional.empty());

    assertNotFound(() ->
      service.requireAccess(
        ORDER_ID,
        authentication,
        new MockHttpSession()
      )
    );

    verifyNoInteractions(currentAccount);
  }

  private void existingOrder() {
    when(orders.findById(ORDER_ID))
      .thenReturn(Optional.of(order));
  }

  private void assertNotFound(Runnable action) {
    assertThatThrownBy(action::run)
      .isInstanceOfSatisfying(
        ResponseStatusException.class,
        exception -> {
          assertThat(exception.getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

          assertThat(exception.getReason())
            .isEqualTo("ORDER_NOT_FOUND");
        }
      );
  }
}
