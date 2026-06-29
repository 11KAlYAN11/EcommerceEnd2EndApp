package com.ecommerce.admin;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 12 — Admin endpoints for business analytics and order management.
 * All require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * GET /api/admin/dashboard
     * Returns: total revenue, order counts by status, product & user counts
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard", dashboardService.getSummary()));
    }

    /**
     * GET /api/admin/revenue?from=2026-01-01T00:00:00&to=2026-12-31T23:59:59
     */
    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success("Revenue report",
                dashboardService.getRevenueReport(from, to)));
    }

    /**
     * GET /api/admin/top-customers?limit=10
     */
    @GetMapping("/top-customers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopCustomers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Top customers",
                dashboardService.getTopCustomers(limit)));
    }

    /**
     * GET /api/admin/orders?status=PENDING&from=...&to=...&page=0&size=20
     * Filter + paginate all orders (admin view)
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Page<Order>>> getAllOrders(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Order> orders = orderRepository.findByFilters(status, from, to,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success("Orders", orders));
    }

    /**
     * PATCH /api/admin/orders/{id}/status
     * Body: { "status": "SHIPPED" }
     */
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(ApiResponse.success("Order status updated",
                orderService.updateStatus(id, newStatus)));
    }
}
