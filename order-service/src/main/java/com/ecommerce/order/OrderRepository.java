package com.ecommerce.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Page<Order> findByUserEmail(String userEmail, Pageable pageable);

    // ── 16.8: admin reporting -- same aggregate queries the monolith had,
    // simpler here since userEmail is a plain column now, not a join through
    // o.user.email. ──────────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal getTotalRevenue();

    @Query("""
        SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o
        WHERE o.status = 'DELIVERED'
        AND o.createdAt BETWEEN :from AND :to
        """)
    BigDecimal getRevenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    @Query("""
        SELECT o.userEmail, SUM(o.totalPrice) as totalSpent
        FROM Order o
        WHERE o.status = 'DELIVERED'
        GROUP BY o.userEmail
        ORDER BY totalSpent DESC
        """)
    List<Object[]> topCustomersBySpend(Pageable pageable);

    // findByFilters used to live here as a JPQL "(:status IS NULL OR o.status = :status)"
    // query -- that's the exact Hibernate 6 / Postgres null-parameter-type-inference bug
    // documented in problems-overcomed.md #12 ("could not determine data type of
    // parameter"). It surfaced here live (16.8) the same way it did in the monolith's
    // product search. Same fix: JpaSpecificationExecutor + OrderSpec, not JPQL.
}
