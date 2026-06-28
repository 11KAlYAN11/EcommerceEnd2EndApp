package com.ecommerce.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // Used when user adds a product that's already in the cart → update qty instead of insert
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
}
