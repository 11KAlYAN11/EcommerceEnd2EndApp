package com.ecommerce.payment;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.Order;
import com.ecommerce.order.OrderRepository;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * Initiate payment for an order.
     * Creates a PENDING payment record and returns a reference ID.
     *
     * In production: this calls the payment gateway API (Razorpay/Stripe),
     * gets back a gateway order ID, and returns it to the frontend.
     * Frontend opens the payment widget. On success/failure, gateway
     * sends a webhook to /payments/confirm or /payments/fail.
     */
    @Transactional
    public PaymentResponse initiatePayment(String email, Long orderId, Payment.PaymentMethod method) {
        Order order = getOrderForUser(email, orderId);

        // Can't initiate payment if one already exists and is completed
        paymentRepository.findByOrderId(orderId).ifPresent(existing -> {
            if (existing.getStatus() == Payment.PaymentStatus.COMPLETED) {
                throw new ConflictException("Payment already completed for order: " + orderId);
            }
            // If PENDING or FAILED → allow re-initiation (user retrying)
            paymentRepository.delete(existing);
        });

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot pay for a cancelled order");
        }

        // Simulate gateway reference — in production this comes from Razorpay/Stripe API
        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalPrice())
                .status(Payment.PaymentStatus.PENDING)
                .method(method)
                .paymentReference(reference)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment initiated: ref={}, orderId={}, amount={}", reference, orderId, order.getTotalPrice());

        return toResponse(saved);
    }

    /**
     * Confirm a payment (simulates gateway success webhook).
     *
     * In production: this endpoint is called by the payment gateway via webhook.
     * It includes a signature we must verify before trusting.
     * We verify: HMAC-SHA256(webhookBody, gatewayWebhookSecret) == signature header.
     *
     * Idempotency: paymentReference UNIQUE constraint means if this webhook
     * arrives twice, the second call returns the existing record gracefully.
     */
    @Transactional
    public PaymentResponse confirmPayment(String email, Long orderId) {
        Order order = getOrderForUser(email, orderId);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending payment found for order: " + orderId));

        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
            log.warn("Payment already confirmed (idempotent call): orderId={}", orderId);
            return toResponse(payment); // idempotent — return existing result
        }

        // Mark payment as completed
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        // Advance order status
        order.setStatus(Order.OrderStatus.CONFIRMED);
        orderRepository.save(order);

        log.info("Payment confirmed: ref={}, orderId={}", payment.getPaymentReference(), orderId);
        return toResponse(payment);
    }

    /**
     * Fail a payment (simulates gateway failure webhook or user abandoning payment).
     * Order stays PENDING — user can retry with initiatePayment again.
     */
    @Transactional
    public PaymentResponse failPayment(String email, Long orderId) {
        Order order = getOrderForUser(email, orderId);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment found for order: " + orderId));

        payment.setStatus(Payment.PaymentStatus.FAILED);
        paymentRepository.save(payment);

        // Order stays PENDING — user can try again
        log.info("Payment failed: ref={}, orderId={}", payment.getPaymentReference(), orderId);
        return toResponse(payment);
    }

    /**
     * Refund a payment — called when order is cancelled after payment.
     * In production: call gateway refund API, then mark as REFUNDED.
     */
    @Transactional
    public PaymentResponse refundPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment found for order: " + orderId));

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new IllegalArgumentException("Can only refund completed payments");
        }

        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);
        log.info("Payment refunded: ref={}, orderId={}", payment.getPaymentReference(), orderId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForOrder(String email, Long orderId) {
        getOrderForUser(email, orderId); // ownership check
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment found for order: " + orderId));
        return toResponse(payment);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Order getOrderForUser(String email, Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getUser().getEmail().equals(email))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .orderId(p.getOrder().getId())
                .amount(p.getAmount())
                .status(p.getStatus())
                .method(p.getMethod())
                .paymentReference(p.getPaymentReference())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
