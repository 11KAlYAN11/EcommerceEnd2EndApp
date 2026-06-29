# Phase 6 — Order Management

## Objective
Implement the order placement flow with full ACID guarantees — atomic stock deduction, price snapshots, and transactional cart clearing.

---

## What We Built
| File | Purpose |
|---|---|
| `order/Order.java` | Order entity with `OrderStatus` enum |
| `order/OrderItem.java` | Individual line item with price snapshot |
| `order/OrderStatus.java` | Enum: PENDING → CONFIRMED → SHIPPED → DELIVERED / CANCELLED |
| `order/OrderService.java` | `placeOrder()` and `cancelOrder()` — both `@Transactional` |
| `order/OrderController.java` | HTTP endpoints |
| `order/dto/OrderResponse.java` | DTO for order data |

## API Endpoints Built
```
POST   /api/orders              → place order from cart
GET    /api/orders              → my order history
GET    /api/orders/{id}         → order detail
DELETE /api/orders/{id}/cancel  → cancel order (if PENDING)
PATCH  /api/orders/{id}/status  → update status (ADMIN only)
```

All require authentication.

---

## Concepts Introduced

### `@Transactional` — Deep Dive (The Most Important Annotation)

```
What it does:
  Wraps the method in a database transaction:
  1. BEGIN transaction (session opens)
  2. Execute method body
  3. COMMIT (if no exception) OR ROLLBACK (if RuntimeException)
  4. Session closes

Without @Transactional:
  Step 1: validate stock → OK
  Step 2: save OrderItem for product A → deduct stock A → saved
  Step 3: save OrderItem for product B → out of stock → exception
  PROBLEM: Product A stock already deducted, but order never created
           → DB is in inconsistent state → stock lost

With @Transactional:
  Step 1: validate stock → OK
  Step 2: save OrderItem for product A → deduct stock A (DB operation pending)
  Step 3: save OrderItem for product B → out of stock → exception thrown
  Spring: exception caught → ROLLBACK → all pending DB operations undone
  → DB returns to pre-transaction state → stock NOT lost
```

### ACID — What Every Java Interview Asks About

```
A — Atomicity:    All steps of the transaction succeed, or NONE do.
                  "All or nothing"

C — Consistency:  Transaction brings DB from one valid state to another.
                  Business rules are never violated mid-transaction.

I — Isolation:    Concurrent transactions don't see each other's in-progress changes.
                  Default: READ COMMITTED (one transaction sees another's committed data)

D — Durability:   Once committed, data survives crashes.
                  Written to disk — not just memory.

Our placeOrder() is ACID:
  Atomic: order + items + stock + cart clear = ONE unit, all or nothing
  Consistent: stock never goes negative (validated before deduction)
  Isolated: concurrent orders use DB locks to prevent double-sell
  Durable: commit → written to PostgreSQL WAL (Write Ahead Log)
```

### The `placeOrder()` Flow — Step by Step

```java
@Transactional
public OrderResponse placeOrder(String email, Long addressId) {
    // 1. Load user and their cart
    User user = userRepository.findByEmail(email).orElseThrow(...);
    Cart cart = cartService.getOrCreateCart(email);

    if (cart.getItems().isEmpty()) {
        throw new IllegalArgumentException("Cart is empty");
    }

    // 2. Validate stock for ALL items before touching anything
    for (CartItem cartItem : cart.getItems()) {
        Product product = cartItem.getProduct();
        if (cartItem.getQuantity() > product.getStockQuantity()) {
            throw new IllegalArgumentException(
                product.getName() + " only has " + product.getStockQuantity() + " in stock");
        }
    }

    // 3. Build Order
    Order order = Order.builder()
        .user(user)
        .status(OrderStatus.PENDING)
        .build();

    // 4. Build OrderItems with PRICE SNAPSHOTS
    BigDecimal total = BigDecimal.ZERO;
    List<OrderItem> items = new ArrayList<>();
    for (CartItem cartItem : cart.getItems()) {
        Product product = cartItem.getProduct();

        // SNAPSHOT: capture price NOW, not a reference to product.price
        OrderItem orderItem = OrderItem.builder()
            .order(order)
            .product(product)
            .quantity(cartItem.getQuantity())
            .priceAtPurchase(product.getPrice())   // ← snapshot
            .build();
        items.add(orderItem);

        // 5. Deduct stock
        product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
        productRepository.save(product);

        total = total.add(product.getPrice().multiply(new BigDecimal(cartItem.getQuantity())));
    }

    order.setItems(items);
    order.setTotalAmount(total);
    Order savedOrder = orderRepository.save(order);   // cascades to OrderItems

    // 6. Clear cart
    cart.getItems().clear();
    cartRepository.save(cart);   // orphanRemoval deletes all CartItem rows

    return OrderResponse.from(savedOrder);
}
```

### Price Snapshot Pattern — Critical Design Decision

```
Why?
  Product: iPhone 15 → ₹79,999 on Jan 1
  User places order on Jan 1 → OrderItem.priceAtPurchase = 79,999
  Apple reduces price to ₹69,999 on Jan 15

WITHOUT snapshot (bad):
  OrderItem has FK to Product → reads product.price dynamically
  Order history on Jan 20: shows ₹69,999 ← WRONG
  Finance can't reconcile → audit failures

WITH snapshot (correct):
  OrderItem.priceAtPurchase = 79,999.00 (stored permanently)
  product.price changes → doesn't affect any existing orders
  Order history on Jan 20: shows ₹79,999 ← CORRECT
  Even if product is deleted/soft-deleted → order history intact
```

### `cancelOrder()` — Stock Restoration

```java
@Transactional
public void cancelOrder(String email, Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow(...);

    // Security: user can only cancel their own orders
    if (!order.getUser().getEmail().equals(email)) {
        throw new AccessDeniedException("Not your order");
    }

    // Business rule: can only cancel PENDING orders
    if (order.getStatus() != OrderStatus.PENDING) {
        throw new IllegalStateException("Cannot cancel " + order.getStatus() + " order");
    }

    // Restore stock for each item
    for (OrderItem item : order.getItems()) {
        Product product = item.getProduct();
        product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
        productRepository.save(product);
    }

    order.setStatus(OrderStatus.CANCELLED);
    orderRepository.save(order);
    // If any step fails → @Transactional rolls back → stock not restored, order stays PENDING
}
```

### Order Status Lifecycle

```
PENDING      → Order placed, awaiting payment
    ↓ payment confirmed
CONFIRMED    → Payment received, being processed
    ↓ admin action
PROCESSING   → Warehouse picking/packing
    ↓ shipped
SHIPPED      → In transit, tracking available
    ↓ delivered
DELIVERED    → Final state (success)

From any state:
    ↓ admin/user cancel
CANCELLED    → Final state (failure path)
              → (stock restored in cancelOrder)

Only PENDING → CANCELLED is user-allowed.
ADMIN can move between any states.
```

### `@Enumerated(EnumType.STRING)` — Never Use ORDINAL

```java
// ORDINAL (default — NEVER USE):
@Enumerated(EnumType.ORDINAL)
private OrderStatus status;

enum OrderStatus {
    PENDING,    // stored as 0
    CONFIRMED,  // stored as 1
    PROCESSING, // stored as 2
    ...
}

// Problem: add "AWAITING_PAYMENT" between PENDING and CONFIRMED:
enum OrderStatus {
    PENDING,            // 0
    AWAITING_PAYMENT,   // 1 ← new
    CONFIRMED,          // 2 ← WAS 1 → all existing "1" rows now mean CONFIRMED
    PROCESSING,         // 3 ← WAS 2 → all existing "2" rows now mean PROCESSING
}
// All existing order data is now WRONG. Production disaster.

// STRING (always use):
@Enumerated(EnumType.STRING)
// Stored as "PENDING", "CONFIRMED" → adding enum values never breaks existing data
```

### `@Table(name = "orders")` — SQL Reserved Keyword

```
"order" is a reserved keyword in SQL (ORDER BY uses it).
Without @Table(name = "orders"):
  Hibernate generates: CREATE TABLE order (...)
  PostgreSQL: syntax error — "order" is not a valid table name

Always name the table "orders" not "order".
Same issue with other reserved words: "user" → "users", "group" → "groups"
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| 500 on place order | Cart empty check missing | Check `cart.getItems().isEmpty()` before proceeding |
| Stock over-sold | No stock validation before deduction | Validate ALL items before deducting ANY |
| Order placed but stock deducted incorrectly | Not using `@Transactional` | All operations must be in one `@Transactional` method |
| Cannot cancel shipped order | Business rule enforced | Only PENDING orders can be cancelled |
| `ORDER` table SQL error | Used `Order` as table name | Use `@Table(name = "orders")` |
| Price changes affect old orders | Reading `product.price` dynamically | Use `OrderItem.priceAtPurchase` snapshot |
| User can cancel other user's orders | No ownership check | Compare `order.getUser().getEmail()` with authenticated email |

---

## Interview Questions

**Q: What is `@Transactional` and what happens if a RuntimeException is thrown?**
> `@Transactional` wraps the method in a DB transaction. If a `RuntimeException` (or its subclasses) is thrown, Spring intercepts it and rolls back ALL database operations made in that method. If it completes normally, Spring commits. This ensures partial state (e.g., stock deducted but order not saved) never persists.

**Q: Explain ACID properties with an example.**
> Using order placement: **Atomicity** — all steps (create order, deduct stock, clear cart) succeed or none do. **Consistency** — stock never goes negative (validated before deduction). **Isolation** — two users placing orders simultaneously use DB locks to prevent overselling the same last item. **Durability** — once committed, the order survives a server crash (written to disk/WAL).

**Q: Why does `@Transactional` only roll back on unchecked exceptions by default?**
> Java design: checked exceptions are "expected errors" (business conditions the caller should handle), unchecked are "unexpected failures." Spring follows this convention. You can override: `@Transactional(rollbackFor = Exception.class)` to roll back on checked exceptions too.

**Q: What is the snapshot pattern? Give an example from the order system.**
> `OrderItem.priceAtPurchase` captures the product's price at the moment the order was placed. When the product price changes later, historical orders show the price that was actually charged — not the current price. This is essential for financial integrity, auditing, and customer trust.

**Q: How do you prevent two users from buying the last item simultaneously?**
> Option 1: `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the product query — locks the row until the transaction commits. Option 2: Optimistic locking with `@Version` — reads the version, deducts stock, writes with `WHERE version = ?`. If another transaction committed first, the version won't match → `OptimisticLockException` → retry. Optimistic is better for low-contention; pessimistic for high-contention.

---

## MFAQ

**Why validate ALL items before deducting stock for ANY?**
If you validate item-by-item and deduct as you go, you might deduct stock for items 1 and 2, then discover item 3 is out of stock. Now stock for items 1 and 2 is wrong. The `@Transactional` rollback would fix it, but the correct pattern is to validate everything upfront — fail fast before touching any data.

**Why is `cart.getItems().clear()` used to clear the cart instead of `cartItemRepository.deleteAll()`?**
`cart.getItems().clear()` leverages `orphanRemoval=true` — Hibernate detects the collection is now empty and deletes all orphaned CartItem rows in one operation. It keeps the cascade/ownership model clean. `deleteAll()` would work but bypasses the entity lifecycle.

**Can the same product appear twice in order items?**
With proper cart logic (using `ifPresentOrElse` to increment quantity instead of adding new items), no. But if it did, you'd want to guard against it in `placeOrder()` by summing quantities by product ID before stock validation.
