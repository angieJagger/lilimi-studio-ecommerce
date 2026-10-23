package pl.lilimi.order.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

import pl.lilimi.order.application.admin.AdminOrderStatusService;
import pl.lilimi.order.application.admin.AdminOrderQueryService;
import pl.lilimi.order.application.admin.AdminOrderReadService;
import pl.lilimi.auth.application.CurrentAccountService;


import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

  private final AdminOrderQueryService queryService;
  private final AdminOrderStatusService statusService;
  private final CurrentAccountService currentAccountService;
  private final AdminOrderReadService readService;

  public AdminOrderController(
    AdminOrderQueryService queryService,
    AdminOrderStatusService statusService,
    CurrentAccountService currentAccountService,
    AdminOrderReadService readService
  ) {
    this.queryService = queryService;
    this.statusService = statusService;
    this.currentAccountService = currentAccountService;
    this.readService = readService;
  }

  @GetMapping
  public AdminOrderPageResponse findOrders(
    Authentication authentication,
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
    @RequestParam(defaultValue = "all") String view,
    @RequestParam(required = false) String status,
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateFrom,
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate dateTo,
    @RequestParam(required = false) String search
  ) {
    var administratorId =
      currentAccountService.requireAdministratorId(authentication);

    return AdminOrderPageResponse.from(
      queryService.findOrders(
        administratorId,
        page,
        size,
        view,
        status,
        dateFrom,
        dateTo,
        search
      )
    );
  }
  @GetMapping("/{id}")
  public AdminOrderDetailsResponse findOrder(
    @PathVariable UUID id
  ) {
    return queryService.findOrder(id);
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(
    Authentication authentication,
    @PathVariable UUID id
  ) {
    var administratorId =
      currentAccountService.requireAdministratorId(authentication);

    readService.markRead(administratorId, id);
  }

  @PatchMapping("/{id}/status")
  public AdminOrderDetailsResponse changeStatus(
    @PathVariable UUID id,
    @Valid @RequestBody ChangeOrderStatusRequest request
  ) {
    return statusService.changeStatus(id, request);
  }
}
