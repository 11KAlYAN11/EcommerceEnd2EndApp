package com.ecommerce.cart;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Was `@ManyToOne Product product` in the monolith. Now `productId` (no FK,
 * cross-service reference) plus a SNAPSHOT of name/price/imageUrl fetched
 * from product-service at add/update time. Refreshed whenever the item is
 * touched, but NOT re-fetched on every cart read -- see CartService.getCart.
 */
@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = {"cart_id", "product_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
