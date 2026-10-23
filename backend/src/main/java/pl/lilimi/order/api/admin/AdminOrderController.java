package pl.lilimi.order.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

import pl.lilimi.order.application.admin.AdminOrderStatusService;
import pl.lilimi.order.application.admin.AdminOrderQueryService;

import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

  private final AdminOrderQueryService queryService;
  private final AdminOrderStatusService statusService;

  public AdminOrderController(
    AdminOrderQueryService queryService,
    AdminOrderStatusService statusService
  ) {
    this.queryService = queryService;
    this.statusService = statusService;
  }

  @GetMapping
  public AdminOrderPageResponse findOrders(
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
    return AdminOrderPageResponse.from(
      queryService.findOrders(
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

  @PatchMapping("/{id}/status")
  public AdminOrderDetailsResponse changeStatus(
    @PathVariable UUID id,
    @Valid @RequestBody ChangeOrderStatusRequest request
  ) {
    return statusService.changeStatus(id, request);
  }
}
