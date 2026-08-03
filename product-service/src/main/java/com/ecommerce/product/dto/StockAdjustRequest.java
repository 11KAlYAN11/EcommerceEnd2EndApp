package com.ecommerce.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** delta: negative to decrement (order placed), positive to restore (order cancelled). */
@Getter
@Setter
public class StockAdjustRequest {
    @NotNull
    private Integer delta;
}
