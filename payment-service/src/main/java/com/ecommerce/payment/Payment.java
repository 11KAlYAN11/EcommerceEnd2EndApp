package com.ecommerce.payment;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** `@OneToOne Order order` (FK into a DB this service no longer has) -> `orderId` (no FK). Same pattern as every prior extraction. */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_reference", columnList = "payment_reference")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private PaymentMethod method;

    @Column(name = "payment_reference", unique = true, length = 255)
    private String paymentReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum PaymentStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REFUNDED
    }

    public enum PaymentMethod {
        CREDIT_CARD,
        DEBIT_CARD,
        UPI,
        NET_BANKING,
        WALLET,
        COD
    }
}
