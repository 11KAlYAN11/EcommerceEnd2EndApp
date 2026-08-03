package com.ecommerce.cart;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Was `@OneToOne User user` in the monolith. Now just the email string
 * pulled straight from the JWT `sub` claim -- no FK, and critically, no
 * call to user-service needed to resolve it. Cart's own reads never touch
 * another service at all; only product-related writes do (see CartService).
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, unique = true)
    private String userEmail;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();
}
