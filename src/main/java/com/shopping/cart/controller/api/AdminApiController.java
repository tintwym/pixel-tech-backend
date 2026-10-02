package com.shopping.cart.controller.api;

import com.shopping.cart.dto.request.RestockRequest;
import com.shopping.cart.dto.request.UpdateOrderStatusRequest;
import com.shopping.cart.dto.response.AdminOrderResponse;
import com.shopping.cart.service.AdminOrderService;
import com.shopping.cart.service.ai.AdminGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin dashboard data. Every endpoint must call {@link AdminGuard#requireAdmin}. */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {
    private final AdminGuard adminGuard;
    private final AdminOrderService adminOrderService;

    public AdminApiController(AdminGuard adminGuard, AdminOrderService adminOrderService) {
        this.adminGuard = adminGuard;
        this.adminOrderService = adminOrderService;
    }

    @GetMapping("/orders")
    public List<AdminOrderResponse> listOrders(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return adminOrderService.listOrders();
    }

    @PatchMapping("/orders/{id}/status")
    public AdminOrderResponse updateOrderStatus(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest body) {
        adminGuard.requireAdmin(request);
        return adminOrderService.updateStatus(id, body.getStatus());
    }

    @PostMapping("/products/{id}/restock")
    public Map<String, Object> restock(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody RestockRequest body) {
        adminGuard.requireAdmin(request);
        int stock = adminOrderService.restock(id, body.getAmount());
        return Map.of("id", id, "stock", stock);
    }
}
