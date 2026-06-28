package com.ecommerce.cart;

import com.ecommerce.common.audit.Auditable;
import com.ecommerce.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user's shopping cart.
 *
 * One cart per user (@OneToOne relationship):
 *   A user has exactly one cart. The cart persists across sessions.
 *   It is created when the user first adds something, and cleared
 *   when the order is placed (cart items are moved to order items).
 *
 * WHY List for cartItems (not Set)?
 *   CartItems are ordered (by insertion time — the user sees them in add order).
 *   Lists maintain insertion order. Sets do not.
 *   Also, the "duplicate entry" issue for @ManyToMany doesn't apply here
 *   because this is @OneToMany, not @ManyToMany.
 *
 * cascade = CascadeType.ALL:
 *   Deleting a Cart automatically deletes all its CartItems.
 *   Adding a CartItem to cart.getItems() and saving cart also saves the item.
 *
 * orphanRemoval = true:
 *   If a CartItem is removed from the List and the Cart is saved,
 *   Hibernate deletes that CartItem row from the DB.
 *   Without this: the item disappears from the list in Java but stays in DB.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();
}
