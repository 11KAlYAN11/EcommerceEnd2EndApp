package com.ecommerce.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * `Product product` (FK) -> `productId` (no FK) + `productName` (NEW
 * snapshot). `priceAtPurchase` already existed in the monolith (ADR B-07);
 * `productName` is the other half of the SAME idea, which the monolith's
 * own comment predicted: "product name could change -- for full accuracy,
 * you'd snapshot product_name too." Extraction forces finishing that.
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

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price_at_purchase", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;
}
