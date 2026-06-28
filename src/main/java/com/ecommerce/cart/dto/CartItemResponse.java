package com.ecommerce.cart.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private String imageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal; // unitPrice × quantity
}
