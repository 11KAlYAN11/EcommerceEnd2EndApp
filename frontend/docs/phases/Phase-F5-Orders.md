# Phase F-5 — Orders

## WHY before code

### Order history: same pattern, different shape

Order listing uses the same `useState + useEffect + pagination` pattern as products. The main difference: orders are user-specific — they require authentication. The `ProtectedRoute` wrapper in `App.jsx` handles this automatically.

```
<Route element={<ProtectedRoute />}>
  <Route path="/orders" element={<OrdersPage />} />
</Route>
```

If the user isn't logged in and tries to visit `/orders`:
1. `ProtectedRoute` checks `token` from `AuthContext`
2. No token → `navigate('/login', { state: { from: location } })`
3. After login, `LoginPage` reads `state.from` and redirects back to `/orders`

---

## Status badge pattern

Orders have a lifecycle: `PENDING → CONFIRMED → SHIPPED → DELIVERED` (or `CANCELLED`).

We map each status to a color class:

```js
export const statusBadgeClass = (status) => {
  const map = {
    PENDING:   'badge-yellow',  // yellow = waiting
    CONFIRMED: 'badge-blue',    // blue = in progress
    SHIPPED:   'badge-blue',
    DELIVERED: 'badge-green',   // green = done
    CANCELLED: 'badge-red',     // red = failed
  }
  return map[status] || 'badge-gray'
}
```

Usage:
```jsx
<span className={`badge ${statusBadgeClass(order.status)}`}>{order.status}</span>
```

---

## Order Detail: useParams + single fetch

```js
const { id } = useParams()   // reads the :id from URL /orders/42

useEffect(() => {
  ordersApi.getById(id)
    .then(res => setOrder(res.data))
}, [id])
```

`useParams()` reads the dynamic segment from the current URL. When `id` changes (user navigates from one order to another), the effect re-runs.

---

## Interview Q&A — Phase F-5

**Q: How does React Router pass URL parameters to a component?**

Using `useParams()`. The route defines the param with `:`:
```jsx
<Route path="/orders/:id" element={<OrderDetailPage />} />
```
The component reads it:
```js
const { id } = useParams()  // id = "42" for URL /orders/42
```
Note: params are always strings — convert to number if needed: `parseInt(id)`

**Q: What is the difference between `useNavigate` and `<Link>`?**

`<Link to="/path">` — declarative navigation inside JSX. Renders an `<a>` tag.
`useNavigate()` — programmatic navigation inside event handlers.

Use `<Link>` for user-visible navigation elements (menu items, clickable cards).
Use `navigate()` for navigation after an action (after form submit, after order creation).

**Q: What is `state` in React Router navigation?**

Navigation state is extra data passed alongside a route change — it doesn't appear in the URL.

```js
// Sender
navigate('/login', { state: { from: location } })

// Receiver (LoginPage)
const { state } = useLocation()
const from = state?.from?.pathname || '/'
// After login:
navigate(from, { replace: true })
```

Used here for "redirect back after login" without polluting the URL with query strings.

**Q: How does `replace: true` differ from a normal navigate?**

Without `replace`: browser history has `/orders → /login → /orders` — back goes to login.
With `replace: true`: history has `/orders → /orders` — login page is not in history, back works correctly.

---

## Files written in this phase

- `src/pages/orders/OrdersPage.jsx` — order history list with status badges and pagination
- `src/pages/orders/OrderDetailPage.jsx` — order detail with items table and summary
- `src/pages/orders/Orders.css` — dark order card and detail layout styles
