package com.ecommerce.order;

import com.ecommerce.address.Address;
import com.ecommerce.common.audit.Auditable;
import com.ecommerce.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a placed order.
 *
 * @Table(name = "orders"):
 *   IMPORTANT: "order" is a reserved SQL keyword.
 *   You CANNOT name the table "order" — the DB will error.
 *   "orders" is the correct table name.
 *
 * Order Status Lifecycle:
 *   PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
 *                    ↓
 *                CANCELLED (from PENDING or CONFIRMED only)
 *
 *   Why @Enumerated(STRING)?
 *   If we used ordinal (0,1,2...) and later inserted a new status
 *   between existing ones, every ordinal shifts → data corruption.
 *   String names are stable regardless of enum order changes.
 *
 * shipping_address_id (snapshot vs live FK):
 *   We store the address ID at order time. But what if the user
 *   later edits their address? The order should show the original address.
 *   Better approach (Phase 6 improvement): snapshot address fields directly
 *   on the order (denormalize for history accuracy).
 *   For now, FK is fine for learning.
 *
 * total_price:
 *   Stored redundantly (can be computed from order_items).
 *   We store it anyway because: prices change. If product price changes
 *   next week, we can still show the correct total at time of purchase.
 *   "What did I pay?" must always return the correct historical answer.
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user", columnList = "user_id"),
    @Index(name = "idx_orders_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    // The address where this order ships to.
    // LAZY because we don't always need to load the full address.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    private Address shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }
}
