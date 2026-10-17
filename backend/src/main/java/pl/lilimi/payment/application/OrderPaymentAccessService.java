package pl.lilimi.payment.application;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.lilimi.auth.application.CurrentAccountService;
import pl.lilimi.order.persistence.OrderRepository;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderPaymentAccessService {

  private final OrderRepository orders;
  private final CurrentAccountService currentAccount;
  private final GuestOrderAccessService guestAccess;

  public OrderPaymentAccessService(
    OrderRepository orders,
    CurrentAccountService currentAccount,
    GuestOrderAccessService guestAccess
  ) {
    this.orders = orders;
    this.currentAccount = currentAccount;
    this.guestAccess = guestAccess;
  }

  public void requireAccess(
    UUID orderId,
    Authentication authentication,
    HttpSession session
  ) {
    var order = orders.findById(orderId)
      .orElseThrow(OrderPaymentAccessService::notFound);

    UUID ownerId = order.getCustomerAccountId();

    if (ownerId == null) {
      if (!guestAccess.hasAccess(session, orderId)) {
        throw notFound();
      }

      return;
    }

    UUID accountId = currentAccount.findAccountId(
      authentication
    );

    if (!ownerId.equals(accountId)) {
      throw notFound();
    }
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "ORDER_NOT_FOUND"
    );
  }
}
