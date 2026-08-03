package com.ecommerce.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceOrderRequest {
    private Long shippingAddressId; // null = use default address
}
