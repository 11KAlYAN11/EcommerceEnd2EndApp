package com.ecommerce.order.dto;

import com.ecommerce.order.Order;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderResponse {
    private Long orderId;
    private Order.OrderStatus status;
    private BigDecimal totalPrice;
    private List<OrderItemResponse> items;
    private LocalDateTime placedAt;
    private Long shippingAddressId;
}
