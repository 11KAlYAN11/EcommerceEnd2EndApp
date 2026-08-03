package com.ecommerce.product;

import java.math.BigDecimal;

public record ProductSnapshot(Long id, String name, BigDecimal price, Integer stockQuantity, boolean active) {
}
