# Phase 7 — Payment Processing

## Objective
Implement a payment lifecycle with idempotency, status tracking, and webhook-style confirmation. Learn why real-world payment flows need deduplication.

---

## What We Built
| File | Purpose |
|---|---|
| `payment/Payment.java` | Payment entity with idempotency key |
| `payment/PaymentStatus.java` | Enum: PENDING, COMPLETED, FAILED, REFUNDED |
| `payment/PaymentMethod.java` | Enum: CREDIT_CARD, UPI, NET_BANKING, COD, etc. |
| `payment/PaymentService.java` | Create, confirm, fail, refund payment |
| `payment/PaymentController.java` | HTTP endpoints |
| `payment/dto/PaymentResponse.java` | DTO for payment data |

## API Endpoints Built
```
POST   /api/payments                  → initiate payment for an order
GET    /api/payments/{id}             → get payment details
GET    /api/payments/order/{orderId}  → payment for a specific order
POST   /api/payments/{id}/confirm     → confirm payment (simulate webhook)
POST   /api/payments/{id}/fail        → fail payment (simulate webhook)
POST   /api/payments/{id}/refund      → refund payment (ADMIN only)
```

---

## Concepts Introduced

### Why Payment Is Separate From Order

```
Naive design: Order has a paymentStatus field
  → Order and payment lifecycle are coupled
  → Can't refund partial payment
  → Can't retry payment on different method
  → Can't have multiple payment attempts for one order

Correct design: Payment is a separate entity
  Order ──── Payment (one-to-one, but Order is created first)

  Order: PENDING (awaiting payment)
       ↓
  Payment: PENDING (payment initiated)
         ↓ payment gateway confirms
  Payment: COMPLETED → Order: CONFIRMED (payment received)
         ↓ or
  Payment: FAILED → Order stays PENDING (user retries)
         ↓ or later
  Payment: REFUNDED → Order: CANCELLED
```

### `paymentReference` — The Idempotency Key

```
Real-world problem: webhook delivered twice
  Payment gateway calls POST /webhook with paymentReference=PAY-12345
  Network glitch → gateway sends it again
  Without idempotency:
    First call: PENDING → COMPLETED, order → CONFIRMED
    Second call: COMPLETED → COMPLETED again (double-credits, double-deductions)

Our solution:
  paymentReference is UNIQUE in the DB (@Column(unique=true))

  In confirmPayment():
  Payment payment = paymentRepository.findById(id).orElseThrow(...);

  if (payment.getStatus() == PaymentStatus.COMPLETED) {
      return PaymentResponse.from(payment); // already done — return existing record
  }

  payment.setStatus(PaymentStatus.COMPLETED);
  // ... update order status
  paymentRepository.save(payment);

  → Second webhook call: finds COMPLETED → returns existing record → no re-processing
  → Safe for any number of retries
```

### Idempotency — The Core Concept

```
An operation is IDEMPOTENT if applying it multiple times has the same effect as applying it once.

HTTP methods by convention:
  GET    → idempotent (reading doesn't change state)
  PUT    → idempotent (setting a value to X multiple times = X)
  DELETE → idempotent (deleting something twice = same result)
  POST   → NOT idempotent by default (creating twice = two records)

Making POST idempotent for payments:
  Strategy 1: Check-before-create (our approach)
    If payment for this order already exists → return it, don't create another

  Strategy 2: Idempotency-Key header
    Client sends: Idempotency-Key: uuid-from-client
    Server stores key → if duplicate → return cached response

  Strategy 3: UNIQUE constraint on paymentReference
    DB enforces at the storage level → duplicate insert throws exception → handled gracefully
```

### The Payment Lifecycle State Machine

```
ORDER PLACED → Payment.status = PENDING
                Order.status  = PENDING

─── Payment Gateway Flow ───────────────────────────────

  User enters card details → gateway processes → calls our webhook

  If SUCCESS:
    POST /api/payments/{id}/confirm
    Payment.status = COMPLETED
    Order.status   = CONFIRMED
    → User gets order confirmation

  If FAILURE:
    POST /api/payments/{id}/fail
    Payment.status = FAILED
    Order.status   = PENDING (user can retry with different method)
    → Stock is NOT restored (order is still valid, just unpaid)

─── After Delivery ─────────────────────────────────────

  If Refund requested:
    POST /api/payments/{id}/refund  (ADMIN only)
    Payment.status = REFUNDED
    Order.status   = CANCELLED
    → Stock should be restored (add to cancelOrder logic)
```

### Fake Gateway Pattern — How We Simulate Payments

```
Real payment gateway (Stripe, Razorpay, PayU):
  1. Client redirects to gateway's payment page
  2. Customer enters card details ON GATEWAY's server (PCI compliance)
  3. Gateway processes → calls our webhook URL with result
  4. Our webhook: update payment status, confirm order

Our simulation:
  1. POST /api/payments → creates PENDING payment (like "redirect to gateway")
  2. POST /api/payments/{id}/confirm → simulates "gateway webhook called"
  3. No actual money moves — purely status state machine

This pattern is correct for learning. In production:
  - Integrate Razorpay/Stripe SDK
  - Add webhook signature verification (HMAC on webhook body)
  - Expose real webhook endpoint
  - Never trust the payment amount from the webhook — re-verify with gateway API
```

### `@Transactional` in Payment Confirmation

```java
@Transactional
public PaymentResponse confirmPayment(Long paymentId) {
    Payment payment = paymentRepository.findById(paymentId).orElseThrow(...);

    // Idempotency check
    if (payment.getStatus() == PaymentStatus.COMPLETED) {
        return PaymentResponse.from(payment);
    }

    // Update payment
    payment.setStatus(PaymentStatus.COMPLETED);
    paymentRepository.save(payment);

    // Update order — both must succeed atomically
    Order order = payment.getOrder();
    order.setStatus(OrderStatus.CONFIRMED);
    orderRepository.save(order);

    return PaymentResponse.from(payment);
}
// If order update fails after payment update → rollback both
// If payment update fails → rollback → order stays PENDING → user retries
```

### Webhook Security (Production Concept)

```
Problem: anyone can call POST /payments/{id}/confirm and confirm a payment without paying.

Production solution:
  Razorpay sends: X-Razorpay-Signature header
  Value: HMAC-SHA256(webhook_body, razorpay_webhook_secret)

  Our webhook handler:
    String expectedSignature = HMAC-SHA256(requestBody, ourWebhookSecret);
    if (!expectedSignature.equals(receivedSignature)) {
        throw new SecurityException("Invalid webhook signature");
    }
    // Only then process the payment

In our current implementation: no signature check (learning project).
In production: always verify webhook signatures.
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| Double payment processing | No idempotency check | Check `payment.getStatus() == COMPLETED` before processing |
| Order stays PENDING after payment | Not updating order status in confirmPayment | Both payment AND order must be updated in same `@Transactional` |
| Payment not found | paymentId vs orderId confusion | Use the right ID — payment has its own ID, order has its own |
| Refund doesn't restore stock | Refund only changes status | Add stock restoration to refund flow (same as cancelOrder) |
| 403 on refund | Refund requires ADMIN | Use `@PreAuthorize("hasRole('ADMIN')")` on refund endpoint |
| `paymentReference` duplicate key | Creating payment twice for same order | Check if payment already exists for this order |

---

## Interview Questions

**Q: What is idempotency and why does it matter for payment APIs?**
> An idempotent operation has the same effect when applied once or many times. Payment webhooks are delivered "at least once" — network retries can cause duplicate calls. Without idempotency, a duplicate webhook could charge the customer twice, or mark an order confirmed twice, corrupting data. Idempotency keys (and UNIQUE constraints on `paymentReference`) ensure the second call is a no-op that returns the same result as the first.

**Q: Why is payment a separate entity instead of a field on Order?**
> A payment has its own lifecycle independent of the order. An order can have multiple payment attempts (first card declined, second succeeds). A payment can be partially refunded. A payment has its own reference number from the gateway. Coupling these into one entity violates Single Responsibility and makes the data model rigid.

**Q: What is a payment gateway? How does it work in production?**
> A payment gateway (Stripe, Razorpay, PayU) is a third-party service that handles card processing. The customer enters card details directly on the gateway's server (not ours) — this is PCI compliance, ensuring card data never touches our servers. The gateway calls our webhook URL with the result. We verify the webhook signature, then update our order/payment status.

**Q: How do you verify that a webhook is genuinely from the payment gateway?**
> Payment gateways sign their webhooks with HMAC-SHA256 using a shared secret. They include the signature in a header (e.g., `X-Razorpay-Signature`). On our server: compute `HMAC-SHA256(requestBody, webhookSecret)` and compare with the received signature. If they don't match, reject the request. This prevents attackers from faking payment confirmations.

**Q: What happens if the payment confirmation step succeeds but the order status update fails?**
> Without `@Transactional`, this creates an inconsistent state: payment is COMPLETED but order is still PENDING. With `@Transactional`, if the order update throws an exception, the entire transaction rolls back — payment reverts to PENDING, order stays PENDING. The user retries. The system is always in a consistent state.

---

## MFAQ

**Why does `failPayment()` not restore stock?**
Stock is deducted when the order is placed, not when payment is confirmed. A failed payment means the user should retry — the order still exists, the items are still reserved conceptually. When the user retries and pays, the order is confirmed. If the user cancels (via `cancelOrder()`), stock is restored then.

**Can we have multiple payments for one order?**
In our current design, one order has one payment (`@OneToOne`). In a real system with retry logic, you'd use `@OneToMany` — each payment attempt creates a new `Payment` record with a new reference. Only one can ever be COMPLETED. Our simplified model works for learning purposes.

**What is PCI DSS?**
Payment Card Industry Data Security Standard — a set of rules that govern how card data must be handled. The key rule: card numbers must never touch your servers unencrypted. Using payment gateways means you're "out of scope" for PCI DSS — the gateway handles card data, you only handle non-sensitive order/payment IDs.
