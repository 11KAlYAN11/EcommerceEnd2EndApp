package com.ecommerce.cart;

import com.ecommerce.product.Product;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a single line item inside a Cart.
 *
 * cart_items is the JOIN between Cart and Product, with extra data (quantity).
 * This is NOT a @ManyToMany — it's @ManyToOne on both sides because we need
 * to store quantity on the relationship itself.
 *
 * @ManyToMany can't hold extra columns. When you need extra data
 * on the join (quantity, price_at_add, notes), model it as an entity.
 *
 * Note: CartItem does NOT extend Auditable.
 *   Cart items are transient — they come and go as users add/remove items.
 *   We don't need to audit their creation time for business purposes.
 *   Keeping the table lean is intentional.
 */
@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = {
        // A cart cannot have two rows for the same product.
        // If user adds the same product twice, we UPDATE quantity, not INSERT again.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
