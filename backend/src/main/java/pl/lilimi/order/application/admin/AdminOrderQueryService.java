package pl.lilimi.order.application.admin;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.hibernate.query.criteria.JpaExpression;

import pl.lilimi.order.api.admin.AdminOrderContactResponse;
import pl.lilimi.order.api.admin.AdminOrderDeliveryResponse;
import pl.lilimi.order.api.admin.AdminOrderDetailsResponse;
import pl.lilimi.order.api.admin.AdminOrderItemResponse;
import pl.lilimi.order.api.admin.AdminOrderSummaryResponse;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.domain.OrderStatus;
import pl.lilimi.order.persistence.OrderItemRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.payment.cancellation.CheckoutExpirationQueue;
import pl.lilimi.payment.cancellation.CheckoutExpirationTaskView;
import pl.lilimi.payment.domain.PaymentAttempt;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Locale;
import java.util.ArrayList;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AdminOrderQueryService {

  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final CheckoutExpirationQueue expirationQueue;

  public AdminOrderQueryService(
    OrderRepository orders,
    OrderItemRepository items,
    CheckoutExpirationQueue expirationQueue
  ) {
    this.orders = orders;
    this.items = items;
    this.expirationQueue = expirationQueue;
  }

  @Transactional(readOnly = true)
  public Page<AdminOrderSummaryResponse> findOrders(
    int page,
    int size,
    String view,
    String status,
    LocalDate dateFrom,
    LocalDate dateTo,
    String search
  ) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid pagination"
      );
    }

    if (
      view == null
        || !Set.of("active", "archive", "all").contains(view)
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid order view"
      );
    }

    if (
      dateFrom != null
        && dateTo != null
        && dateFrom.isAfter(dateTo)
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid date range"
      );
    }

    String searchPattern = createSearchPattern(search);
    OrderStatus selectedStatus = parseStatus(status);
    ZoneId zone = ZoneId.of("Europe/Warsaw");

    Instant from;
    Instant until;

    try {
      from = dateFrom == null
        ? null
        : dateFrom.atStartOfDay(zone).toInstant();

      until = dateTo == null
        ? null
        : dateTo.plusDays(1).atStartOfDay(zone).toInstant();
    } catch (DateTimeException exception) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid date range"
      );
    }

    Specification<Order> filter = (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();

      if ("active".equals(view)) {
        var reviewQuery = query.subquery(Integer.class);
        var task = reviewQuery.from(CheckoutExpirationTaskView.class);
        var attempt = reviewQuery.from(PaymentAttempt.class);

        reviewQuery.select(builder.literal(1));
        reviewQuery.where(
          builder.equal(
            task.get("paymentAttemptId"),
            attempt.get("id")
          ),
          builder.equal(
            attempt.get("orderId"),
            root.get("id")
          ),
          builder.equal(task.get("status"), "needs_review")
        );

        predicates.add(
          builder.or(
            root.get("status").in(
              OrderStatus.NEW,
              OrderStatus.PROCESSING
            ),
            builder.exists(reviewQuery)
          )
        );
      } else if ("archive".equals(view)) {
        predicates.add(
          root.get("status").in(
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED
          )
        );
      }

      if (selectedStatus != null) {
        predicates.add(
          builder.equal(root.get("status"), selectedStatus)
        );
      }

      if (searchPattern != null) {
        var orderNumber = (
          (JpaExpression<UUID>) root.<UUID>get("id")
        ).cast(String.class);

        predicates.add(
          builder.or(
            builder.like(
              builder.lower(orderNumber),
              searchPattern,
              '!'
            ),
            builder.like(
              builder.lower(root.get("contact").<String>get("email")),
              searchPattern,
              '!'
            ),
            builder.like(
              builder.lower(root.get("contact").<String>get("fullName")),
              searchPattern,
              '!'
            )
          )
        );
      }

      if (from != null) {
        predicates.add(
          builder.greaterThanOrEqualTo(
            root.<Instant>get("createdAt"),
            from
          )
        );
      }

      if (until != null) {
        predicates.add(
          builder.lessThan(
            root.<Instant>get("createdAt"),
            until
          )
        );
      }

      return builder.and(predicates.toArray(Predicate[]::new));
    };

    var sorting = Sort.by(
      Sort.Order.desc("createdAt"),
      Sort.Order.desc("id")
    );

    var result = orders.findAll(
      filter,
      PageRequest.of(page, size, sorting)
    );

    var orderIds = result.getContent().stream()
      .map(order -> order.getId())
      .toList();

    var cancellations = expirationQueue.findSummaries(orderIds);

    return result.map(order ->
      AdminOrderSummaryResponse.from(
        order,
        cancellations.get(order.getId())
      )
    );
  }

  private String createSearchPattern(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.strip();

    if (normalized.length() > 254) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Search phrase is too long"
      );
    }

    String escaped = normalized
      .toLowerCase(Locale.ROOT)
      .replace("!", "!!")
      .replace("%", "!%")
      .replace("_", "!_");

    return "%" + escaped + "%";
  }

  private OrderStatus parseStatus(String value) {
    if (value == null) {
      return null;
    }

    try {
      return OrderStatus.fromValue(value);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid order status"
      );
    }
  }

  @Transactional(readOnly = true)
  public AdminOrderDetailsResponse findOrder(UUID id) {
    var order = orders.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Order not found"
      ));

    var delivery = order.getDelivery();

    var deliveryResponse = new AdminOrderDeliveryResponse(
      delivery.getKind(),
      delivery.getMethodId(),
      delivery.getAddressLine1(),
      delivery.getAddressLine2(),
      delivery.getPostalCode(),
      delivery.getCity(),
      delivery.getCountryCode()
    );

    var itemResponses = items.findByOrder_IdOrderByPositionAsc(id)
      .stream()
      .map(AdminOrderItemResponse::from)
      .toList();

    var cancellation = expirationQueue.findSummaries(
      List.of(order.getId())
    ).get(order.getId());

    return new AdminOrderDetailsResponse(
      order.getId(),
      order.getVersion(),
      order.getCreatedAt(),
      order.getStatus().getValue(),
      order.getLanguage(),
      order.getCurrency(),
      AdminOrderContactResponse.from(order.getContact()),
      deliveryResponse,
      itemResponses,
      order.getSubtotalInGrosz(),
      order.getDeliveryPriceInGrosz(),
      order.getTotalInGrosz(),
      cancellation
    );
  }
}
