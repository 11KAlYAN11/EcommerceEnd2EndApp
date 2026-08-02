package com.ecommerce.notification.dto;

import java.math.BigDecimal;

/**
 * Replaces the monolith's OrderItem entity in the email template context.
 * Only the 3 fields the template actually renders — nothing else crosses
 * the service boundary.
 */
public record OrderItemLine(
        String productName,
        int quantity,
        BigDecimal priceAtPurchase
) {
}
