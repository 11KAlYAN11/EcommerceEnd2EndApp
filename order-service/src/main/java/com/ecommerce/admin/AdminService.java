package com.ecommerce.admin;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.order.OrderService;
import com.ecommerce.order.OrderSpec;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.product.ProductClient;
import com.ecommerce.user.UserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 16.8 — the piece that closes the gap flagged after the UI cutover: the
 * monolith's AdminDashboardService lived in-process with OrderRepository,
 * ProductRepository, and UserRepository all in the same JVM. Here, revenue
 * / orders-by-status / top-customers / filtered-orders are still pure
 * order-service data (no network call needed — same as the monolith, just
 * on userEmail instead of a user.email join). Only totalProducts and
 * totalUsers need to leave this service, via ProductClient/UserClient —
 * same required-fetch pattern as everywhere else in this migration.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ProductClient productClient;
    private final UserClient userClient;

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        BigDecimal totalRevenue = orderRepository.getTotalRevenue();

        List<Object[]> statusCounts = orderRepository.countByStatus();
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        long totalOrders = 0;
        for (Object[] row : statusCounts) {
            String status = row[0].toString();
            Long count = (Long) row[1];
            ordersByStatus.put(status, count);
            totalOrders += count;
        }

        long totalProducts = productClient.countActive();
        long totalUsers = userClient.count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalOrders", totalOrders);
        summary.put("ordersByStatus", ordersByStatus);
        summary.put("totalProducts", totalProducts);
        summary.put("totalUsers", totalUsers);
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueReport(LocalDateTime from, LocalDateTime to) {
        BigDecimal revenue = orderRepository.getRevenueBetween(from, to);
        return Map.of("from", from.toString(), "to", to.toString(), "revenue", revenue);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopCustomers(int limit) {
        List<Object[]> rows = orderRepository.topCustomersBySpend(PageRequest.of(0, limit));
        return rows.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("email", row[0]);
            entry.put("totalSpent", row[1]);
            return entry;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrdersFiltered(
            Order.OrderStatus status, LocalDateTime from, LocalDateTime to, int page, int size) {
        Specification<Order> spec = Specification.where(OrderSpec.statusEquals(status))
                .and(OrderSpec.createdAfter(from))
                .and(OrderSpec.createdBefore(to));
        return orderRepository.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(orderService::toResponse);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "Order " + orderId + " not found"));
        order.setStatus(newStatus);
        return orderService.toResponse(orderRepository.save(order));
    }
}
