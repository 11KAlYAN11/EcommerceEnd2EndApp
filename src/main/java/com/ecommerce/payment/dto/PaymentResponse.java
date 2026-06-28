package com.ecommerce.payment.dto;

import com.ecommerce.payment.Payment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {
    private Long paymentId;
    private Long orderId;
    private BigDecimal amount;
    private Payment.PaymentStatus status;
    private Payment.PaymentMethod method;
    private String paymentReference;
    private LocalDateTime createdAt;
}
