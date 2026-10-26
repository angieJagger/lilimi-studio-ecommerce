package pl.lilimi.order.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import pl.lilimi.order.api.CreateOrderRequest;
import pl.lilimi.order.domain.Order;
import pl.lilimi.order.persistence.OrderConfirmationEmailRepository;
import pl.lilimi.order.persistence.OrderRepository;
import pl.lilimi.order.persistence.OrderSubmissionRepository;
import pl.lilimi.security.application.PublicFormRateLimiter;

import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@Validated
public class OrderSubmissionService {

  private final OrderService orderService;
  private final OrderRepository orderRepository;
  private final OrderSubmissionRepository submissionRepository;
  private final OrderConfirmationEmailRepository confirmationEmails;
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final PublicFormRateLimiter rateLimiter;

  public OrderSubmissionService(
    OrderService orderService,
    OrderRepository orderRepository,
    OrderSubmissionRepository submissionRepository,
    OrderConfirmationEmailRepository confirmationEmails,
    PublicFormRateLimiter rateLimiter
  ) {
    this.orderService = orderService;
    this.orderRepository = orderRepository;
    this.submissionRepository = submissionRepository;
    this.confirmationEmails = confirmationEmails;
    this.rateLimiter = rateLimiter;
  }

  @Transactional
  public Order submit(
    @NotNull UUID idempotencyKey,
    @NotNull @Valid CreateOrderRequest request
  ) {
    return submitInternal(
      idempotencyKey,
      request,
      null,
      null
    );
  }

  @Transactional
  public Order submit(
    @NotNull UUID idempotencyKey,
    @NotNull @Valid CreateOrderRequest request,
    UUID customerAccountId
  ) {
    return submitInternal(
      idempotencyKey,
      request,
      customerAccountId,
      null
    );
  }

  @Transactional
  public Order submitFromSource(
    @NotNull UUID idempotencyKey,
    @NotNull @Valid CreateOrderRequest request,
    UUID customerAccountId,
    @NotBlank String source
  ) {
    return submitInternal(
      idempotencyKey,
      request,
      customerAccountId,
      source
    );
  }

  private Order submitInternal(
    UUID idempotencyKey,
    CreateOrderRequest request,
    UUID customerAccountId,
    String source
  ) {
    String requestHash = hash(request);

    submissionRepository.reserve(
      idempotencyKey,
      requestHash
    );

    var submission = submissionRepository.lock(idempotencyKey);

    if (!submission.requestHash().equals(requestHash)) {
      throw new OrderSubmissionConflictException();
    }

    if (submission.orderId() != null) {
      var existingOrder = orderRepository.findById(
          submission.orderId()
        )
        .orElseThrow(() -> new IllegalStateException(
          "Order referenced by submission does not exist"
        ));

      if (!Objects.equals(
        existingOrder.getCustomerAccountId(),
        customerAccountId
      )) {
        throw new OrderSubmissionConflictException();
      }

      return existingOrder;
    }

    if (source != null) {
      long retryAfter = rateLimiter.reserveOrder(source);

      if (retryAfter > 0) {
        throw new OrderRateLimitExceededException(retryAfter);
      }
    }

    var order = orderService.createOrder(
      request,
      customerAccountId
    );

    orderRepository.flush();

    submissionRepository.attachOrder(
      idempotencyKey,
      order.getId()
    );

    confirmationEmails.enqueue(order.getId());

    return order;
  }

  private String hash(CreateOrderRequest request) {
    byte[] json = mapper.writeValueAsBytes(request);

    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(json);

      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
        "SHA-256 is unavailable",
        exception
      );
    }
  }
}
