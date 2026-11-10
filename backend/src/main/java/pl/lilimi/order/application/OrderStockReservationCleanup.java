package pl.lilimi.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import pl.lilimi.order.persistence.OrderRepository;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class OrderStockReservationCleanup {

  private static final Logger log = LoggerFactory.getLogger(OrderStockReservationCleanup.class);
  private static final Duration RESERVATION_DURATION = Duration.ofHours(1);

  private final OrderRepository orders;
  private final OrderStockReservationService reservations;
  private final Clock clock;

  public OrderStockReservationCleanup(OrderRepository orders, OrderStockReservationService reservations, Clock clock) {
    this.orders = orders;
    this.reservations = reservations;
    this.clock = clock;
  }

  @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
  public void cleanUp() {
    var cutoff = clock.instant().minus(RESERVATION_DURATION);
    for (var orderId : orders.findDueStockReservationOrderIds(cutoff)) {
      try {
        reservations.processDueOrder(orderId, cutoff);
      } catch (RuntimeException exception) {
        log.error("Could not process stock reservation for order {}", orderId, exception);
      }
    }
  }
}
