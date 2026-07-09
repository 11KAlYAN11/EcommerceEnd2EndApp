# Phase F-7 — Admin Panel

## WHY before code

### Two layers of protection for admin routes

```
<Route element={<AdminRoute />}>
  <Route path="/admin" element={<AdminDashboardPage />} />
</Route>
```

`AdminRoute` does two checks:
1. Is there a token? (logged in?)
2. Does the token's `roles` array include `ROLE_ADMIN`?

```js
export default function AdminRoute() {
  const { token, isAdmin } = useAuth()
  if (!token)   return <Navigate to="/login" state={{ from: location }} />
  if (!isAdmin) return <Navigate to="/" />
  return <Outlet />
}
```

Why two checks? A logged-in regular user (`ROLE_USER`) who manually types `/admin` in the address bar should get kicked to homepage, not login. The `isAdmin` check handles this.

### isAdmin comes from the JWT — never trust client-side only

```js
// From AuthContext.jsx
const isAdmin = Array.isArray(user?.roles)
  ? user.roles.includes('ROLE_ADMIN')
  : user?.role === 'ROLE_ADMIN'
```

We read `roles` from the JWT payload. But this is only the **frontend gate** — it prevents the UI from showing admin buttons to regular users. The real security is on the backend:

```java
// Spring Boot — every admin endpoint is double-protected
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/orders")
public Page<OrderResponse> getAllOrders(...)
```

Even if a malicious user modified their JWT to fake `ROLE_ADMIN`, the Spring Security check on the backend would still reject them (it verifies the JWT signature).

---

## CRUD pattern in AdminProductsPage

Every CRUD feature follows the same pattern:

```
1. Load data on mount (useEffect → fetch → setProducts)
2. Open modal for create/edit
3. Submit form → API call → close modal → reload data
4. Delete → confirm dialog → API call → reload data
```

The modal is reused for both Create and Edit:
```js
function openCreate() {
  setEditing(null)     // null = create mode
  setForm(EMPTY)
  setShowModal(true)
}

function openEdit(p) {
  setEditing(p)        // non-null = edit mode
  setForm({ name: p.name, price: p.price, ... })
  setShowModal(true)
}

async function handleSave() {
  if (editing) {
    await adminApi.updateProduct(editing.id, form)  // PUT
  } else {
    await adminApi.createProduct(form)               // POST
  }
}
```

One modal, one save handler, branching on `editing`.

---

## The form shorthand pattern

```js
const f = (key) => (e) => setForm(prev => ({ ...prev, [key]: e.target.value }))
```

Usage: `onChange={f('name')}` instead of `onChange={e => setForm(prev => ({...prev, name: e.target.value}))}`

This is a **curried function** — `f('name')` returns a new function `(e) => ...`. The `[key]` is computed property name — it uses the variable `key` as the object property name.

---

## Order status management

```js
<select
  value={o.status}
  onChange={e => updateStatus(o.id, e.target.value)}
>
  {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
</select>
```

When admin changes the dropdown → `updateStatus(orderId, newStatus)` → `PATCH /api/admin/orders/{id}/status` → Spring Boot validates and updates → page reloads.

The select's `value={o.status}` makes it controlled — it always shows the current server state.

---

## Interview Q&A — Phase F-7

**Q: What is the difference between authentication and authorization?**

Authentication: who are you? (login — verifying identity)
Authorization: what can you do? (admin check — verifying permissions)

In our app:
- Authentication: JWT token presence = logged in
- Authorization: JWT `roles` array = what you can access

**Q: Why use `window.confirm()` for delete? What's a better alternative?**

`window.confirm()` is quick and simple — shows native browser dialog. Problems:
1. Can't be styled to match your dark theme
2. Blocks the main thread
3. Some browsers suppress it in iframes

Better alternative: a custom `ConfirmModal` component — same `Modal.jsx` component with a "Are you sure?" message and Cancel/Delete buttons. We skip this for simplicity.

**Q: What does `@PreAuthorize("hasRole('ADMIN')")` do?**

It's Spring Security's method-level security. Before executing the method, Spring checks whether the current JWT contains `ROLE_ADMIN` in the roles claim. If not, it throws `AccessDeniedException` → 403 Forbidden.

This is the backend enforcement — the frontend `AdminRoute` is just UX (prevent confusion), not security.

**Q: What is RBAC (Role-Based Access Control)?**

RBAC = permissions are assigned to roles, roles are assigned to users. Users inherit permissions from their roles.

Our system: `ROLE_USER` = browse + buy. `ROLE_ADMIN` = also manage products/categories/orders.

Alternative: ABAC (Attribute-Based Access Control) — permissions based on attributes like department, resource owner, time of day. More flexible but more complex.

---

## Files written in this phase

- `src/pages/admin/AdminDashboardPage.jsx` — stat cards + quick navigation links
- `src/pages/admin/AdminProductsPage.jsx` — table + create/edit/delete with modal
- `src/pages/admin/AdminCategoriesPage.jsx` — simple category CRUD
- `src/pages/admin/AdminOrdersPage.jsx` — order table with status filter + inline status update
- `src/pages/admin/Admin.css` — stat grid, quick links, form grid styles
- `src/components/layout/AdminRoute.jsx` — double guard: token + isAdmin
