package pl.lilimi.order.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lilimi.order.application.admin.AdminOrderQueryService;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

  private final AdminOrderQueryService queryService;

  public AdminOrderController(AdminOrderQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping
  public AdminOrderPageResponse findOrders(
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
  ) {
    return AdminOrderPageResponse.from(
      queryService.findOrders(page, size)
    );
  }
}
