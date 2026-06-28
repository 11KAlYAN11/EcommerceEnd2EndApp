package com.ecommerce.product;

import com.ecommerce.category.Category;
import com.ecommerce.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a product available for purchase.
 *
 * WHY BigDecimal for price (not double or float)?
 *   double price = 0.1 + 0.2 → 0.30000000000000004  (floating point error!)
 *   BigDecimal is EXACT decimal arithmetic — required for money.
 *   In financial systems, using float/double for money is a critical bug.
 *   Always use BigDecimal for prices, amounts, and rates.
 *
 *   @Column(precision=10, scale=2):
 *     precision = total digits (e.g., 99999999.99 = 10 digits)
 *     scale = digits after decimal (2 → cents: 99.99)
 *     DB stores as DECIMAL(10,2) — exact, no floating point.
 *
 * stock_quantity:
 *   Tracks available inventory. When an order is placed, we decrement this.
 *   If stock_quantity = 0, product is out of stock.
 *   Concurrency concern (Phase 6): two users placing orders simultaneously
 *   could both read stock=1, both decrement → stock goes to -1.
 *   We'll solve this with @Lock (pessimistic locking) in Phase 6.
 *
 * active flag:
 *   Products are NEVER hard-deleted in an e-commerce system.
 *   If a product is discontinued, active=false hides it from listings.
 *   Why? Orders reference products by product_id. Deleting a product
 *   would break old order history — "what did I buy?" becomes unanswerable.
 *   Soft delete with active=false preserves referential integrity.
 */
@Entity
@Table(name = "products", indexes = {
    // Indexes speed up common queries:
    // "find products by category" → very frequent query
    @Index(name = "idx_products_category", columnList = "category_id"),
    // "search products by name" → used in search feature (Phase 4)
    @Index(name = "idx_products_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // Many products → one category
    // LAZY: don't load category unless explicitly needed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}
