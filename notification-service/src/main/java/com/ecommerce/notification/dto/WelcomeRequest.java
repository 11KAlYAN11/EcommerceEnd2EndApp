package com.ecommerce.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record WelcomeRequest(
        @Email @NotBlank String recipientEmail,
        @NotBlank String firstName
) {
}
