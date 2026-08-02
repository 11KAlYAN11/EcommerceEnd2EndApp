package com.ecommerce.notification.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LowStockAlertRequest(
        @NotBlank String productName,
        @Min(0) int remainingStock
) {
}
