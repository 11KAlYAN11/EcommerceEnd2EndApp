package com.ecommerce.admin;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
 * Phase 12 — Admin Dashboard using advanced JPQL queries.
 *
 * All methods are ADMIN only — user-facing data aggregated for business insight.
 * These are expensive queries (aggregates, joins) — add caching in Phase 8 style if needed.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        // Total revenue
        BigDecimal totalRevenue = orderRepository.getTotalRevenue();

        // Order counts by status — Object[] { status, count }
        List<Object[]> statusCounts = orderRepository.countByStatus();
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        long totalOrders = 0;
        for (Object[] row : statusCounts) {
            String status = row[0].toString();
            Long count = (Long) row[1];
            ordersByStatus.put(status, count);
            totalOrders += count;
        }

        // Product and user counts
        long totalProducts = productRepository.count();
        long totalUsers = userRepository.count();

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
        return Map.of(
                "from", from.toString(),
                "to", to.toString(),
                "revenue", revenue
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopCustomers(int limit) {
        // Object[] { email, totalSpent }
        List<Object[]> rows = orderRepository.topCustomersBySpend(PageRequest.of(0, limit));
        return rows.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("email", row[0]);
            entry.put("totalSpent", row[1]);
            return entry;
        }).toList();
    }
}
