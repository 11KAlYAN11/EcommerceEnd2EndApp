package com.ecommerce.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderCancellationRequest(
        @Email @NotBlank String recipientEmail,
        @NotBlank String firstName,
        @NotNull Long orderId,
        @NotNull BigDecimal totalPrice
) {
}
