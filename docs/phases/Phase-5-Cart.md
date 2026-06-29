# Phase 5 — Shopping Cart

## Objective
Build a per-user shopping cart with lazy creation, stock validation, and proper item management.

---

## What We Built
| File | Purpose |
|---|---|
| `cart/Cart.java` | Cart entity — one per user |
| `cart/CartItem.java` | Individual item in the cart |
| `cart/CartService.java` | Cart operations with stock validation |
| `cart/CartController.java` | HTTP endpoints, uses `@AuthenticationPrincipal` |
| `cart/dto/CartResponse.java` | DTO for cart + items |
| `cart/dto/CartItemRequest.java` | Add/update item input |

## API Endpoints Built
```
GET    /api/cart              → view my cart
POST   /api/cart/items        → add item (or increment if exists)
PUT    /api/cart/items/{id}   → update item quantity
DELETE /api/cart/items/{id}   → remove item from cart
DELETE /api/cart              → clear entire cart
```

All endpoints require a valid JWT token (authenticated user).

---

## Concepts Introduced

### `@AuthenticationPrincipal` — Getting the Logged-In User

```
Problem: how does the controller know WHICH user is making this request?

Without @AuthenticationPrincipal (wrong way):
  @PostMapping("/cart/items")
  public ResponseEntity<?> addItem(@RequestParam String email, ...) {
      // Client sends their own email — they could send anyone's email!
  }

With @AuthenticationPrincipal (correct way):
  @PostMapping("/items")
  public ResponseEntity<?> addItem(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestBody CartItemRequest request) {
      String email = userDetails.getUsername(); // comes from the validated JWT
      // Client can't fake this — it comes from the token we signed
  }
```

Flow:
```
1. JwtAuthFilter validates token → extracts email → sets SecurityContextHolder
2. @AuthenticationPrincipal reads from SecurityContextHolder
3. Controller gets userDetails.getUsername() = the authenticated email
4. Service uses email to load the user's cart

Security: the email in the token is signed — client cannot modify it without invalidating the signature
```

### Lazy Cart Creation — Create on First Use

```java
// Anti-pattern: create cart for every new user on registration
// Problem: most users who register never add anything to cart → wasted rows

// CORRECT — lazy creation:
public Cart getOrCreateCart(String email) {
    User user = userRepository.findByEmail(email).orElseThrow(...);
    return cartRepository.findByUser(user)
        .orElseGet(() -> {
            Cart cart = Cart.builder().user(user).build();
            return cartRepository.save(cart);
        });
}
// Cart is created the FIRST TIME the user tries to do anything with their cart
// Users who never use cart → no cart row in DB
```

This is the "lazy initialization" pattern — defer resource creation until first use.

### `ifPresentOrElse` — Idempotent Add Item

```java
public void addItem(String email, CartItemRequest request) {
    Cart cart = getOrCreateCart(email);
    Product product = productRepository.findById(request.getProductId())...;

    // Validate stock
    if (request.getQuantity() > product.getStockQuantity()) {
        throw new IllegalArgumentException("Only " + product.getStockQuantity() + " in stock");
    }

    // Check if this product is already in the cart
    Optional<CartItem> existing = cart.getItems().stream()
        .filter(item -> item.getProduct().getId().equals(product.getId()))
        .findFirst();

    existing.ifPresentOrElse(
        item -> item.setQuantity(item.getQuantity() + request.getQuantity()), // increment
        () -> cart.getItems().add(CartItem.builder()                          // add new
            .cart(cart)
            .product(product)
            .quantity(request.getQuantity())
            .build())
    );
    cartRepository.save(cart);
}
```

This is idempotent in the sense that "add iPhone 15 qty 1" twice = iPhone 15 with qty 2 in cart (not two separate cart items).

### `orphanRemoval = true` — Remove Item from List = Delete from DB

```java
// In Cart entity:
@OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
private List<CartItem> items = new ArrayList<>();

// Service:
public void removeItem(String email, Long cartItemId) {
    Cart cart = getOrCreateCart(email);
    cart.getItems().removeIf(item -> item.getId().equals(cartItemId));
    cartRepository.save(cart);  // → Hibernate DELETEs the CartItem row
}

// Without orphanRemoval:
// Item removed from list in memory but STILL IN DB → ghost rows
// Next time user loads cart → item reappears (Hibernate re-loads from DB)
```

### Stock Validation — Before Adding to Cart

```
Scenario: Only 2 iPhones in stock
  User tries to add 3 to cart
  → throw IllegalArgumentException("Only 2 in stock")
  → GlobalExceptionHandler returns 400 Bad Request

Also validate when updating quantity:
  User has 1 iPhone in cart, tries to set qty to 5
  → throw IllegalArgumentException("Only 2 in stock")

Note: stock is deducted when ORDER is placed, not when added to cart.
  Cart = wishlist (no stock reservation)
  Order placement = actual stock deduction (atomic, @Transactional)
```

### Cart → Order Transition

```
Cart is temporary. Order is permanent.

When order is placed (Phase 6):
  1. Read all CartItems
  2. For each CartItem: create OrderItem (snapshot price at that moment)
  3. Deduct stock from each product
  4. Clear the cart
  All of this is ONE @Transactional — all or nothing

If payment fails: cart is already cleared. User rebuilds cart.
If stock runs out mid-order: rollback → no order, no stock deduction, cart intact
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| 401 on all cart endpoints | No JWT token | Cart requires auth — add `Authorization: Bearer <token>` |
| `getOrCreateCart()` not accessible | Method was package-private | Must be `public` — OrderService is in different package |
| Adding same product creates duplicate cart items | Not checking for existing item | Use `ifPresentOrElse` — find existing and increment |
| Item removed from list but still in DB | `orphanRemoval=false` (default) | Set `orphanRemoval=true` on Cart.items |
| Stock over-sold | Cart doesn't reserve stock | Stock check in `addItem`, atomic deduction in `placeOrder` |
| Cart not found after registration | Cart is lazy-created | Cart is created on first add, not at registration |

---

## Interview Questions

**Q: What is `@AuthenticationPrincipal` and why is it secure?**
> It injects the authenticated user's `UserDetails` from `SecurityContextHolder`, which was populated by `JwtAuthFilter` after validating the JWT. The email comes from the server-signed token — the client cannot spoof it without the secret key. This is how you get "the current user" in a controller without trusting client-provided user IDs.

**Q: What is the lazy initialization pattern? How does it apply to cart creation?**
> Lazy initialization defers resource creation until first use. Instead of creating a cart row for every registered user (most of whom may never use it), we create it on first cart access. `Optional.orElseGet()` either returns the existing cart or creates a new one in a single operation.

**Q: What is `orphanRemoval = true` and how does it differ from `CascadeType.REMOVE`?**
> `CascadeType.REMOVE`: deleting the parent (Cart) deletes children (CartItems). `orphanRemoval = true`: removing a child from the parent's collection (in memory) triggers a DELETE for that child in the DB. Without `orphanRemoval`, removing an item from `cart.getItems()` and saving the cart does nothing to the DB — the item row persists.

**Q: Why does the cart not reserve stock? When is stock actually deducted?**
> Cart is a wishlist — multiple users can have the same product in their cart simultaneously. Stock is deducted atomically when an order is placed (`@Transactional`). If two users both place an order and only 1 item is in stock, one succeeds and one gets a validation error. Reserving stock in the cart would require a reservation system with expiry timers — complex and unnecessary for most e-commerce.

**Q: How would you handle a race condition where two users order the last item simultaneously?**
> Using a database-level pessimistic lock (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the product row during order placement, or optimistic locking with `@Version`. The `@Transactional` + DB constraint ensures only one thread can decrement stock below zero.

---

## MFAQ

**Why is `getOrCreateCart()` a separate method and not inlined?**
Both `CartService` and `OrderService` need to get the user's cart. Extracting it as a named method avoids duplication and makes the intent clear. `public` visibility is required because `OrderService` is in a different package.

**Can a user have multiple carts?**
In our design, no — `Cart` has a `@OneToOne` relationship with `User` (one cart per user). The `user_id` column in `carts` table has a unique constraint. If you wanted a "saved for later" feature, you'd add a second cart type (a different entity), not multiple carts.

**Why not use sessions for the cart (like many e-commerce sites)?**
Session-based carts work for anonymous users but are lost when the session expires. We want carts to persist across logins and devices. A DB-backed cart persists indefinitely and is accessible from any device with the same account.
