# Phase F-6 — Payment

## WHY before code

### Why navigation state instead of URL params for the order?

When the user places an order in `CartPage`, they're redirected to `OrderDetailPage`. If we wanted to go to a payment page directly after placing an order, we'd pass the order data via navigation state:

```js
// CartPage after placing order
navigate(`/payment`, { state: { order } })

// PaymentPage reads it
const { state } = useLocation()
const order = state?.order
```

**Why not `/payment?orderId=42`?**
The orderId in the URL would work, but we'd need another API call to re-fetch the order. Passing the order in state avoids that extra request. The trade-off: if the user refreshes the page, `state` is lost (it's in memory, not in the URL). For payment pages this is acceptable — refreshing a payment page should start over anyway.

---

## Payment method selection with radio inputs

```jsx
const METHODS = ['COD', 'UPI', 'CARD', 'NET']
const [method, setMethod] = useState('COD')

{METHODS.map(m => (
  <label className={`method-option ${method === m.id ? 'selected' : ''}`}>
    <input type="radio" name="method" value={m.id}
      checked={method === m.id}
      onChange={() => setMethod(m.id)}
    />
    ...
  </label>
))}
```

The radio `<input>` is hidden (`display: none`) — we style the `<label>` instead and use the `selected` class to show which is active. The `name="method"` groups radios so only one can be selected.

---

## Simulating payment processing

In a real app, you'd integrate Razorpay / Stripe SDK here. We simulate:

1. User selects method + clicks Pay
2. `setProcessing(true)` — button disabled, shows spinner
3. `paymentsApi.processPayment({ orderId, paymentMethod })` → POST to backend
4. Backend creates a `Payment` record and updates order status to `CONFIRMED`
5. On success → `setSuccess(true)` → show green screen → redirect to order detail

The `finally` block always re-enables the button:
```js
try { ... } catch { ... } finally { setProcessing(false) }
```

---

## Interview Q&A — Phase F-6

**Q: What is the difference between POST and GET requests?**

GET: fetches data, no body, idempotent (same result if called multiple times). Cached by browsers.
POST: sends data, has a body, NOT idempotent (creates something new each time). Not cached.

Payment = POST because each call creates a new payment record.

**Q: What is idempotency and why does it matter for payments?**

Idempotency means: calling the same operation multiple times has the same effect as calling it once.

For payments, this is critical. If the network times out after the server processes a payment:
- User's browser doesn't know if it succeeded
- User might click "Pay" again
- Without idempotency: second call creates a duplicate charge
- With idempotency key: server detects duplicate and returns the original result

Real payment providers (Stripe, Razorpay) use an `Idempotency-Key` header for this.

**Q: What is navigation state vs query parameters?**

Query params (`?orderId=42`): in the URL, persist on refresh, bookmarkable, visible to users.
Navigation state: in memory only, lost on refresh, not in URL, suitable for sensitive or temporary data.

---

## Files written in this phase

- `src/pages/payments/PaymentPage.jsx` — payment method selection + confirmation
- `src/pages/payments/Payment.css` — method option cards, success screen
