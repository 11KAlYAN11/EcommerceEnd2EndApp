package com.ecommerce.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What used to be "pass the User and Order entities" is now "pass exactly
 * the fields the email needs." The caller (order-service, later) is
 * responsible for assembling this from its own data — this service has
 * no idea what an Order or a User even look like anymore.
 */
public record OrderConfirmationRequest(
        @Email @NotBlank String recipientEmail,
        @NotBlank String firstName,
        @NotNull Long orderId,
        @NotBlank String status,
        @NotNull BigDecimal totalPrice,
        List<OrderItemLine> items,
        LocalDateTime placedAt
) {
}
