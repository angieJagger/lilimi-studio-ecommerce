package pl.lilimi.payment.access;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GuestOrderAccessService {

  private static final String ATTRIBUTE =
    GuestOrderAccessService.class.getName() + ".orders";

  private static final int MAX_ORDERS = 50;

  public void grant(HttpSession session, UUID orderId) {
    Objects.requireNonNull(session, "Session is required");
    Objects.requireNonNull(orderId, "Order ID is required");

    synchronized (session) {
      var orderIds = new ArrayList<UUID>();

      if (session.getAttribute(ATTRIBUTE) instanceof List<?> saved) {
        for (var value : saved) {
          if (value instanceof UUID id) {
            orderIds.add(id);
          }
        }
      }

      orderIds.remove(orderId);
      orderIds.add(orderId);

      while (orderIds.size() > MAX_ORDERS) {
        orderIds.remove(0);
      }

      session.setAttribute(
        ATTRIBUTE,
        new ArrayList<>(orderIds)
      );
    }
  }

  public boolean hasAccess(
    HttpSession session,
    UUID orderId
  ) {
    if (session == null || orderId == null) {
      return false;
    }

    try {
      synchronized (session) {
        return session.getAttribute(ATTRIBUTE)
          instanceof List<?> saved
          && saved.contains(orderId);
      }
    } catch (IllegalStateException exception) {
      // The session may have expired or been invalidated.
      return false;
    }
  }
}
