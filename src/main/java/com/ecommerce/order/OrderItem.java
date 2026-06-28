package com.ecommerce.order;

import com.ecommerce.product.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents one line item within an Order.
 *
 * CRITICAL DESIGN DECISION — price_at_purchase:
 *   We store the product price AT THE TIME OF PURCHASE, not a FK to product.price.
 *
 *   Why? Imagine:
 *     User buys iPhone 15 for ₹79,999 on Jan 1
 *     Apple drops price to ₹69,999 on Jan 15
 *     User views order history on Jan 20
 *
 *   If we referenced product.price (live price):
 *     Order history shows ₹69,999 — WRONG. User paid ₹79,999.
 *
 *   By storing price_at_purchase:
 *     Order history always shows ₹79,999 — CORRECT.
 *
 *   This is called "snapshotting" — capture the state at transaction time.
 *   It's a fundamental pattern in financial/e-commerce systems.
 *   Similarly, product name could change — for full accuracy,
 *   you'd snapshot product_name too (we'll add that in Phase 6).
 *
 * product (FK still exists):
 *   We keep the product FK for navigation (view product details from order).
 *   But price comes from price_at_purchase, not product.price.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // Snapshot: the price this item was purchased at — immutable after creation
    @Column(name = "price_at_purchase", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;
}
