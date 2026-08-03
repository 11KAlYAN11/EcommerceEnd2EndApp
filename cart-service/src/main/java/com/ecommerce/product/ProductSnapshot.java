package com.ecommerce.product;

import java.math.BigDecimal;

/** Shape of the bits of product-service's ProductResponse that cart-service actually needs. */
public record ProductSnapshot(
        Long id,
        String name,
        BigDecimal price,
        String imageUrl,
        boolean active,
        Integer stockQuantity
) {
}
