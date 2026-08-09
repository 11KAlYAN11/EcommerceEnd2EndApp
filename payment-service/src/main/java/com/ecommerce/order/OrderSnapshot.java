package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderSnapshot(Long orderId, String status, BigDecimal totalPrice) {
}
