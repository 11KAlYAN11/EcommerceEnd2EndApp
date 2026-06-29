package com.ecommerce.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 12 — Advanced Querying examples.
 *
 * JOIN FETCH — the N+1 fix:
 *   Without it: load 10 orders → 10 separate queries to load each order's items
 *   With it:    one JOIN query loads orders + items together
 *
 * Aggregate queries (SUM, COUNT, AVG):
 *   JPQL supports aggregate functions just like SQL.
 *   Return types: Long for counts, BigDecimal for sums, Object[] for projections.
 *
 * Projection interface:
 *   Instead of loading the full entity, map query result to a plain interface.
 *   Spring instantiates a proxy that implements the interface — no entity needed.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, Order.OrderStatus status, Pageable pageable);

    // ── Phase 12: JOIN FETCH — solve N+1 ────────────────────────────────────
    // Without JOIN FETCH: loading N orders → N+1 queries (one per order to load items)
    // WITH JOIN FETCH: one query with LEFT JOIN loads orders + all their items
    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        WHERE o.user.id = :userId
        ORDER BY o.createdAt DESC
        """)
    List<Order> findByUserIdWithItems(@Param("userId") Long userId);

    // Single order with all items pre-loaded — no lazy loading after this
    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        WHERE o.id = :orderId
        """)
    Order findByIdWithItems(@Param("orderId") Long orderId);

    // ── Aggregate queries for admin dashboard ────────────────────────────────

    // Total revenue (sum of all DELIVERED order totals)
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal getTotalRevenue();

    // Revenue in a date range
    @Query("""
        SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o
        WHERE o.status = 'DELIVERED'
        AND o.createdAt BETWEEN :from AND :to
        """)
    BigDecimal getRevenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Count orders by status
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    // Top N customers by total spend
    @Query("""
        SELECT o.user.email, SUM(o.totalPrice) as totalSpent
        FROM Order o
        WHERE o.status = 'DELIVERED'
        GROUP BY o.user.email
        ORDER BY totalSpent DESC
        """)
    List<Object[]> topCustomersBySpend(Pageable pageable);

    // Orders in a date range (admin view)
    @Query("""
        SELECT o FROM Order o
        WHERE (:status IS NULL OR o.status = :status)
        AND (:from IS NULL OR o.createdAt >= :from)
        AND (:to IS NULL OR o.createdAt <= :to)
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findByFilters(
            @Param("status") Order.OrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
