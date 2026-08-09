package com.ecommerce.payment;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.OrderClient;
import com.ecommerce.order.OrderSnapshot;
import com.ecommerce.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Two calls to order-service replace what used to be direct entity access:
 *   - getOrder(orderId)  -- was `orderRepository.findById(...)`, ownership
 *     was a local filter; now it's whatever order-service's own GET
 *     /orders/{id} decides (same forwarded-JWT pattern as everywhere else).
 *   - markConfirmed(orderId) -- was `order.setStatus(CONFIRMED)` in the
 *     SAME transaction as the payment write. Now it's a separate HTTP call
 *     with its own commit. Same known gap as order-service's stock
 *     adjustment: if this service crashes between saving payment=COMPLETED
 *     and calling order-service, the order is left PENDING despite a
 *     completed payment. A saga/outbox pattern (Phase 17+) is the real fix;
 *     not solved here, stated plainly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;

    @Transactional
    public PaymentResponse initiatePayment(Long orderId, Payment.PaymentMethod method) {
        OrderSnapshot order = orderClient.getOrder(orderId);

        paymentRepository.findByOrderId(orderId).ifPresent(existing -> {
            if (existing.getStatus() == Payment.PaymentStatus.COMPLETED) {
                throw new ConflictException("Payment already completed for order: " + orderId);
            }
            paymentRepository.delete(existing);
        });

        if ("CANCELLED".equals(order.status())) {
            throw new IllegalArgumentException("Cannot pay for a cancelled order");
        }

        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(order.totalPrice())
                .status(Payment.PaymentStatus.PENDING)
                .method(method)
                .paymentReference(reference)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment initiated: ref={}, orderId={}, amount={}", reference, orderId, order.totalPrice());
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse confirmPayment(Long orderId) {
        orderClient.getOrder(orderId); // ownership/existence check

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No pending payment found for order: " + orderId));

        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
            log.warn("Payment already confirmed (idempotent call): orderId={}", orderId);
            return toResponse(payment);
        }

        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        orderClient.markConfirmed(orderId);

        log.info("Payment confirmed: ref={}, orderId={}", payment.getPaymentReference(), orderId);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse failPayment(Long orderId) {
        orderClient.getOrder(orderId);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order: " + orderId));

        payment.setStatus(Payment.PaymentStatus.FAILED);
        paymentRepository.save(payment);

        log.info("Payment failed: ref={}, orderId={}", payment.getPaymentReference(), orderId);
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForOrder(Long orderId) {
        orderClient.getOrder(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order: " + orderId));
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .orderId(p.getOrderId())
                .amount(p.getAmount())
                .status(p.getStatus())
                .method(p.getMethod())
                .paymentReference(p.getPaymentReference())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
