package com.ecommerce.cart.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CartResponse {
    private Long cartId;
    private List<CartItemResponse> items;
    private int totalItems;       // total number of distinct products
    private BigDecimal totalPrice; // sum of all subtotals
}
