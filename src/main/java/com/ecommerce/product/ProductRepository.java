package com.ecommerce.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 9 — Search queries explained:
 *
 * Simple LIKE search (Phase 4 — already existed):
 *   findByNameContainingIgnoreCaseAndActiveTrue
 *   SQL: WHERE LOWER(name) LIKE LOWER('%keyword%') AND active = true
 *   Simple but only searches the name field.
 *
 * Multi-field search (Phase 9):
 *   @Query with JPQL — searches name AND description together.
 *   LOWER() → case-insensitive match.
 *
 * Price range filter (Phase 9):
 *   findByPriceBetweenAndActiveTrue
 *   Spring Data derives SQL from method name: price BETWEEN min AND max
 *
 * PostgreSQL Full-Text Search (FTS) — the professional approach:
 *   Uses tsvector/tsquery — tokenizes text, ranks results by relevance.
 *   We show it here as a named query for learning.
 *   In production you'd add a GIN index on the tsvector column.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    List<Product> findByCategoryId(Long categoryId);

    // ── Phase 9: Advanced Search ────────────────────────────────────────────

    // Multi-field LIKE search: searches name AND description
    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Price range filter
    Page<Product> findByPriceBetweenAndActiveTrue(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // searchWithFilters is now handled via JpaSpecificationExecutor in ProductService
    // Reason: Hibernate 6 cannot bind parameters used in both IS NULL and LIKE in JPQL
}
