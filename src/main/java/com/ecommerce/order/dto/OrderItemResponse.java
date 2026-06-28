package com.ecommerce.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OrderItemResponse {
    private Long orderItemId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal priceAtPurchase;  // historical price — not live product price
    private BigDecimal subtotal;
}
