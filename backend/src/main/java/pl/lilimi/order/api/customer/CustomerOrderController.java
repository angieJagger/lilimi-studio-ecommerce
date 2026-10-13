package pl.lilimi.order.api.customer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.auth.application.CurrentAccountService;
import pl.lilimi.order.application.customer.CustomerOrderQueryService;

import java.util.UUID;

@RestController
@RequestMapping("/api/account/orders")
public class CustomerOrderController {

  private final CustomerOrderQueryService queryService;
  private final CurrentAccountService currentAccountService;

  public CustomerOrderController(
    CustomerOrderQueryService queryService,
    CurrentAccountService currentAccountService
  ) {
    this.queryService = queryService;
    this.currentAccountService = currentAccountService;
  }

  @GetMapping
  public CustomerOrderPageResponse findOrders(
    Authentication authentication,
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
  ) {
    var accountId = currentAccountService.findAccountId(authentication);

    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return CustomerOrderPageResponse.from(
      queryService.findOrders(accountId, page, size)
    );
  }

  @GetMapping("/{id}")
  public CustomerOrderDetailsResponse findOrder(
    Authentication authentication,
    @PathVariable UUID id
  ) {
    var accountId = currentAccountService.findAccountId(authentication);

    if (accountId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return queryService.findOrder(accountId, id);
  }
}
