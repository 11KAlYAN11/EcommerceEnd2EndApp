package com.ecommerce.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Two changes from the monolith, both the exact improvement its own code
 * comments already called for:
 *
 * 1. `User user` -> `userEmail` (String, from the JWT, no lookup needed --
 *    same pattern as cart-service's Cart.userEmail).
 *
 * 2. `Address shippingAddress` (a live FK into user-service's DB, which no
 *    longer exists here) -> five plain snapshot columns. The monolith's own
 *    Order.java comment said: "Better approach (Phase 6 improvement):
 *    snapshot address fields directly on the order (denormalize for history
 *    accuracy). For now, FK is fine for learning." Splitting into services
 *    doesn't just permit that improvement, it requires it -- there's no FK
 *    to fall back on anymore.
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user", columnList = "user_email"),
    @Index(name = "idx_orders_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    // Shipping address SNAPSHOT -- all nullable, since an order can be
    // placed with no address on file (matches monolith behavior exactly).
    @Column(name = "shipping_street")
    private String shippingStreet;
    @Column(name = "shipping_city")
    private String shippingCity;
    @Column(name = "shipping_state")
    private String shippingState;
    @Column(name = "shipping_pincode")
    private String shippingPincode;
    @Column(name = "shipping_country")
    private String shippingCountry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED
    }
}
