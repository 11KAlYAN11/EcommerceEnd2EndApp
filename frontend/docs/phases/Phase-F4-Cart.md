# Phase F-4 — Cart

## WHY before code

### The Cart Problem: Shared state across unrelated components

The cart count appears in the **Navbar** (top-level). The cart data is loaded in **CartPage** (deep inside routes). These two components are siblings in the tree — they can't communicate directly via props.

This is a classic cross-cutting concern. The solution: **CartContext**.

```
App
├── Navbar            ← reads itemCount from CartContext
└── Routes
    └── CartPage      ← reads items, calls updateItem/removeItem
    └── ProductListPage ← calls refreshCart after add
```

`CartContext` holds:
- `itemCount` — total quantity across all items (badge in Navbar)
- `refreshCart()` — re-fetches count from server

When `ProductListPage` calls `refreshCart()` → `CartContext` fetches `/api/cart` → sets `itemCount` → Navbar badge updates. No prop threading required.

---

## The cart API and optimistic updates

What we do: **pessimistic updates** (wait for server, then show result).

```js
async function updateQty(itemId, qty) {
  await cartApi.updateItem(itemId, qty)  // wait for server
  await loadCart()                        // re-fetch to show confirmed state
}
```

What production apps do: **optimistic updates** (update UI immediately, rollback on error).

```js
async function updateQty(itemId, qty) {
  setCart(prev => ({...prev, items: prev.items.map(i =>    // update UI first
    i.id === itemId ? {...i, quantity: qty} : i
  )}))
  try {
    await cartApi.updateItem(itemId, qty)    // confirm with server
  } catch {
    await loadCart()                          // rollback on failure
  }
}
```

We use pessimistic for simplicity — it always reflects server truth but feels slower.

---

## Order total calculation

```js
const total = items.reduce((sum, item) => sum + item.price * item.quantity, 0)
```

`Array.reduce` — accumulates a value by iterating. The 0 is the initial value of `sum`.

```
items = [{price: 500, qty: 2}, {price: 1000, qty: 1}]
reduce: 0 + (500 × 2) = 1000
        1000 + (1000 × 1) = 2000
total = 2000
```

---

## Placing an order from CartPage

```
User clicks "Place Order"
  → ordersApi.create({ shippingAddress, paymentMethod: 'COD' })
  → POST /api/orders
  → Spring Boot creates Order, clears Cart, returns OrderResponse
  → refreshCart()  ← cart is now empty
  → navigate(`/orders/${order.id}`)  ← redirect to new order
```

The cart is cleared on the server after order creation. We call `refreshCart()` so the Navbar badge goes to 0.

---

## Interview Q&A — Phase F-4

**Q: What is Context vs Redux?**

Context: built into React, good for state that rarely changes (auth, theme, cart count). Every consumer re-renders when the value changes — can cause performance issues if the value is large and changes often.

Redux: external library, more complex setup, but has selective subscriptions (only re-render when your slice changes). Needed for large-scale apps with complex shared state.

For our cart count: Context is perfect — it's small, changes only on add/remove/clear.

**Q: What is `async/await` vs `.then()`?**

Both handle Promises. `async/await` is syntactic sugar:

```js
// .then() chain
cartApi.addItem(id, qty)
  .then(() => refreshCart())
  .then(() => toast.success('Added'))
  .catch(err => toast.error(err.message))
  .finally(() => setAdding(false))

// async/await (same thing)
try {
  await cartApi.addItem(id, qty)
  await refreshCart()
  toast.success('Added')
} catch (err) {
  toast.error(err.message)
} finally {
  setAdding(false)
}
```

`async/await` reads like synchronous code — easier to follow for sequential operations.

**Q: What does `finally` do in a try-catch?**

`finally` always runs, whether the try succeeded or the catch ran. Perfect for:
- Resetting loading state
- Closing database connections
- Hiding spinners

```js
setAdding(true)
try { ... } catch { ... } finally { setAdding(false) }
// setAdding(false) runs even if the API throws
```

---

## Files written in this phase

- `src/pages/cart/CartPage.jsx` — full cart with update/remove/clear, order placement
- `src/pages/cart/Cart.css` — cart layout, item card, summary card styles
