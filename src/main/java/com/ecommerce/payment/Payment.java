package com.ecommerce.payment;

import com.ecommerce.common.audit.Auditable;
import com.ecommerce.order.Order;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a payment transaction for an order.
 *
 * @OneToOne with Order:
 *   Each order has exactly one payment record.
 *   @JoinColumn on this side means payments table holds the FK (order_id).
 *
 * payment_reference:
 *   The ID returned by the payment gateway (Razorpay order ID, Stripe charge ID).
 *   This is how we correlate our record with the gateway's record.
 *   Indexed because webhooks from the gateway include this ID and we must
 *   look up the payment quickly by it.
 *
 * PaymentStatus lifecycle:
 *   PENDING → user initiated checkout but hasn't paid yet
 *   COMPLETED → payment gateway confirmed payment
 *   FAILED → payment declined or error
 *   REFUNDED → money returned to user (after cancellation)
 *
 * PaymentMethod:
 *   Stored as a string enum. Tells us HOW the user paid.
 *   Useful for analytics: "what % of users pay via UPI vs card?"
 *
 * Idempotency (Phase 7 deep-dive):
 *   The payment_reference field is unique.
 *   If the payment gateway sends us the same webhook twice (it happens!),
 *   the second INSERT will fail with a unique constraint violation.
 *   We catch that error and respond "already processed" — no double payment.
 *   This is idempotency: processing the same request twice has the same effect
 *   as processing it once.
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_reference", columnList = "payment_reference")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private PaymentMethod method;

    // Gateway's transaction identifier — unique per transaction
    @Column(name = "payment_reference", unique = true, length = 255)
    private String paymentReference;

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
