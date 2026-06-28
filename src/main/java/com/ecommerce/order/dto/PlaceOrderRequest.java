package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceOrderRequest {

    // Which address to ship to (must belong to the user)
    // null = use default address
    private Long shippingAddressId;
}
