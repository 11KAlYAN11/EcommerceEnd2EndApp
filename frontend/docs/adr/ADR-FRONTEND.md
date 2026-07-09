# Architecture Decision Record — Frontend (React + Vite)

**Project**: ShopEase E-Commerce SPA  
**Status**: Accepted  
**Date**: 2026-07-09  
**Scope**: All frontend architectural decisions across Phases F-0 to F-7

---

## Index of Decisions

| # | Decision | Phase |
|---|---|---|
| F-01 | React 18 + Vite over alternatives | F-0 |
| F-02 | Axios with a single response-unwrap interceptor | F-2 |
| F-03 | Context API (Auth, Cart, Toast) — no Redux | F-1, F-4 |
| F-04 | JWT in localStorage with ProtectedRoute guard | F-1 |
| F-05 | CSS custom properties (variables) for dark theme | F-0 |
| F-06 | Two-state search pattern (input vs committed) | F-3 |
| F-07 | Pessimistic cart updates (server-first) | F-4 |
| F-08 | Flipkart-style product detail page layout | F-3 |
| F-09 | AdminRoute double guard (route + API) | F-7 |
| F-10 | Image fallback via onError handler | F-3 |

---

## F-01 — React 18 + Vite over Alternatives

**Context**  
The backend returns JSON. A browser cannot display `{ "price": 79999 }` as a product card. Something must turn data → pixels. Three realistic choices: React, Angular, plain HTML+JS.

**Decision**  
React 18 with Vite as the build tool.

**Why React over Angular?**  
Angular is a full framework (opinionated router, forms, DI, HTTP client, CLI, decorators). It's excellent for large enterprise teams but has a steep learning curve and generates verbose boilerplate. React is a focused UI library — pick only what you need. React is also the dominant choice in product companies and interviews.

**Why React over plain HTML + jQuery?**  
The UI has state that must stay in sync across multiple components simultaneously:
- Navbar shows cart item count  
- Cart page shows the same items  
- Product page "Add to Cart" button updates both

With jQuery, keeping these in sync manually is error-prone — miss one update and the UI is stale. React's virtual DOM diffing + component re-renders make synchronisation automatic.

**Why Vite over Create-React-App?**  
- Dev server starts in ~300ms vs CRA's ~8s (native ESM, no bundling in dev)  
- HMR (Hot Module Replacement) applies changes without page reload  
- CRA is officially unmaintained as of 2023  
- Vite's proxy config (`/api → localhost:8080`) eliminates CORS issues in dev

**Trade-offs accepted**  
- React ecosystem changes frequently — hooks, suspense, server components require ongoing learning  
- No built-in solution for server state (React Query or SWR would improve caching — left as future enhancement)

---

## F-02 — Axios with a Single Response-Unwrap Interceptor

**Context**  
Every API response from the backend is wrapped in a standard envelope:
```json
{ "success": true, "message": "...", "data": { ... }, "timestamp": "..." }
```
Without an interceptor, every component would need to unwrap the envelope manually:
```js
const res = await productsApi.getAll()
const products = res.data.data.content  // .data (axios) .data (ApiResponse) .content (Page)
```
This is repetitive, error-prone, and leaks the envelope shape into every component.

**Decision**  
A single Axios response interceptor in `src/api/axios.js` unwraps `response.data` once:
```js
api.interceptors.response.use(response => response.data)  // returns ApiResponse
```
After this, every API call returns the `ApiResponse` object directly. Components access:
```js
const res = await productsApi.getAll()
const products = res.data.content  // res = ApiResponse, .data = Page<Product>
```

**Also**: The request interceptor auto-attaches the JWT from localStorage to every request:
```js
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```
No component ever manually handles auth headers.

**Trade-offs accepted**  
- The interceptor return value `response.data` is the `ApiResponse` wrapper — callers must remember to use `.data` for the actual payload. Documented explicitly in `Phase-F2-API-CORS.md`.  
- Error responses (4xx, 5xx) go to `interceptors.response.use(_, error => Promise.reject(error.response?.data))` — errors are also unwrapped to the `ApiResponse` shape so components get `{ success: false, message: "..." }`.

---

## F-03 — Context API (Auth, Cart, Toast) — No Redux

**Context**  
Three pieces of state must be shared across the entire component tree:
1. **Auth state** — who is logged in, their JWT, their roles → needed by Navbar, ProtectedRoute, every API call  
2. **Cart count** — number of items in cart → needed by Navbar badge  
3. **Toast notifications** — success/error messages → triggered from anywhere, displayed in one place

**Decision**  
Three React Contexts: `AuthContext`, `CartContext`, `ToastContext`. Each has its own Provider component. All three wrap the app in `main.jsx`.

**Why not Redux / Zustand?**  
Redux requires: store, reducers, actions, action types, dispatchers, selectors, middleware (Thunk/Saga for async). For three shared state values, this is severe over-engineering. Context API + `useState` + `useReducer` handles this cleanly with zero extra dependencies.

Zustand would be a lighter option — appropriate if the app grows to 10+ shared state slices.

**Pattern used**  
```jsx
// Each context exports a typed hook:
export const useAuth = () => useContext(AuthContext)
export const useCart = () => useContext(CartContext)
export const useToast = () => useContext(ToastContext)

// Components consume:
const { token, user, isAdmin } = useAuth()
const { itemCount, refreshCart } = useCart()
const toast = useToast()
toast.success('Added to cart!')
```

**CartContext specifically**  
Stores `itemCount` for the Navbar badge (number on the cart icon). Does not store full cart data — that lives as local state in `CartPage`. Why? `CartPage` needs the full cart object with items; `Navbar` only needs the count. Loading the full cart into context would re-render the entire tree on every cart change.

**Trade-offs accepted**  
- Context re-renders all consumers when the value changes. For 3 contexts with infrequent updates (login/logout, add to cart), this is acceptable. For high-frequency updates (e.g., live stock count), consider memoization or Zustand.

---

## F-04 — JWT in localStorage with ProtectedRoute Guard

**Context**  
After login, the JWT must persist across page refreshes. Two options: `localStorage` or `httpOnly` cookie.

`localStorage` is readable by JavaScript → XSS risk. `httpOnly` cookies are not readable by JavaScript → XSS-safe. However, cookies require backend `Set-Cookie` configuration and CSRF protection for state-changing requests.

**Decision**  
Store JWT in `localStorage` for this project. Acknowledge the security trade-off explicitly.

Route protection via `ProtectedRoute` (wraps private routes) and `AdminRoute` (wraps admin routes):
```jsx
// ProtectedRoute: redirects to /login if no token
const { token } = useAuth()
if (!token) return <Navigate to="/login" replace />

// AdminRoute: redirects to / if not admin (second check after ProtectedRoute)
const { isAdmin } = useAuth()
if (!isAdmin) return <Navigate to="/" replace />
```

**isAdmin check handles both token shapes**  
The backend JWT payload has `roles: ["ROLE_USER", "ROLE_ADMIN"]` (array). The isAdmin check handles both:
```js
Array.isArray(user?.roles)
  ? user.roles.includes('ROLE_ADMIN')
  : user?.role === 'ROLE_ADMIN'
```

**Auth on page refresh**  
`AuthContext.useEffect` reads the token from `localStorage` on mount and re-populates auth state. The JWT expiry is decoded from the token payload (`exp` field) — expired tokens are cleared automatically.

**Trade-offs accepted**  
- `localStorage` XSS risk — production hardening should move to `httpOnly` cookie + CSRF token  
- Token expiry is checked client-side — a server returning 401 also triggers logout (handled in Axios error interceptor)

---

## F-05 — CSS Custom Properties (Variables) for Dark Theme

**Context**  
The UI must support a dark theme throughout. Two approaches: CSS class toggling (add `class="dark"` to `<body>`) or CSS custom properties (variables) set once on `:root` with dark values.

**Decision**  
All colours, backgrounds, borders, and shadows are defined as CSS custom properties on `:root` in `src/index.css`. Every component uses `var(--color-name)` — no hardcoded colour values anywhere.

```css
:root {
  --bg:        #0f172a;   /* deep dark blue */
  --bg-card:   #1e293b;
  --bg-hover:  #334155;
  --primary:   #6366f1;   /* indigo */
  --text:      #f1f5f9;
  --text-muted:#94a3b8;
  --success:   #10b981;
  --danger:    #ef4444;
  --warning:   #f59e0b;
  --border:    #334155;
}
```

**Why not Tailwind CSS?**  
Tailwind generates utility classes at build time. It's excellent for large teams with design systems. For a learning project, writing plain CSS with variables teaches fundamentals that are invisible inside Tailwind's abstraction. Custom properties also handle theming with less config.

**Why not CSS modules?**  
CSS modules scope styles per component, preventing accidental global leaks. Chosen not to use them because the project is small (no naming collisions in practice) and global utility classes (`btn`, `badge`, `card`, `form-input`) are reused across many components — CSS modules would require importing the same file everywhere or duplicating styles.

**Structure**  
- `src/index.css` — global variables, resets, global utility classes (`btn`, `badge`, `card`, `table`, `pagination`)  
- `src/pages/products/Products.css` — page-specific styles for ProductList + ProductDetail  
- `src/pages/*/` — one `.css` file per feature page  
- Components reference variables: `color: var(--text)`, never `color: #f1f5f9`

**Trade-offs accepted**  
- No automatic light/dark toggle (would require a toggle button + `data-theme` attribute switching) — defaults to dark always  
- CSS custom properties are not supported in IE11 (not a concern for modern browsers)

---

## F-06 — Two-State Search Pattern (Input vs Committed)

**Context**  
A naive single-state search triggers an API call on every keystroke:
```jsx
const [search, setSearch] = useState('')
onChange={e => setSearch(e.target.value)}  // API call on every character
```
Typing "iphone" fires 6 API calls. Only the last one matters. Intermediate calls waste bandwidth and cause race conditions (earlier slow response can overwrite a faster newer response).

**Decision**  
Two separate state variables:
```jsx
const [searchInput, setSearchInput] = useState('')  // controlled input — updates on every keystroke
const [search, setSearch] = useState('')             // committed value — only updates on form submit
```
`useEffect` depends only on `search` (the committed value):
```jsx
useEffect(() => { fetchProducts() }, [search, categoryId, page])
```
The API is called only when the user presses Search or Enter — never on keystroke.

**Alternative**: debounce (`setTimeout` + `clearTimeout` on every keystroke, fire after 300ms of inactivity). Debounce feels more "live search." Two-state is chosen here for simplicity and explicit user intent — the user decides when to search.

**Trade-offs accepted**  
- UX: user must press the button. Debounce would feel more responsive. Acceptable here.  
- Clearing: `setSearch('')` + `setSearchInput('')` both must be called on reset — easy to miss one.

---

## F-07 — Pessimistic Cart Updates (Server-First)

**Context**  
Two approaches to UI updates after a mutation:

**Optimistic**: Update UI immediately, then sync with server. If server fails, roll back.  
**Pessimistic**: Wait for server confirmation, then update UI.

For cart operations (add, remove, quantity change), optimistic updates feel faster. But cart has financial implications — showing a quantity that the server rejected (out of stock race condition) would mislead the user.

**Decision**  
Pessimistic updates. Every cart action waits for the API response before updating UI:
```js
await cartApi.addItem(product.id, qty)   // wait for server
await refreshCart()                       // then re-fetch authoritative cart state
toast.success('Added to cart')
```

`refreshCart()` re-fetches the full cart from the server. This guarantees the UI shows exactly what the server believes the cart contains — stock limits, price updates, and server-side validation are all reflected.

**Trade-offs accepted**  
- ~100-200ms latency before UI responds to a button click (network round-trip)  
- Two sequential API calls (addItem + getCart) — could be reduced to one if the backend returned the updated cart in the addItem response

---

## F-08 — Flipkart-Style Product Detail Page Layout

**Context**  
A standard product detail page shows: image, name, price, description, add to cart. This is minimal. Real e-commerce sites (Flipkart, Amazon) show: multiple images, MRP vs selling price with discount %, highlights bullets, bank offers, delivery date checker, specifications table, customer reviews with rating breakdown, and similar products.

**Decision**  
`ProductDetailPage.jsx` implements a Flipkart-style layout:

**Left column (sticky gallery)**  
- 4 thumbnail buttons — clicking switches the main image  
- Gallery generated from single Unsplash URL by appending different query params (`?crop=entropy`, `?sat=-100`, `?flip=h`) — simulates a multi-photo product  
- Quantity stepper + Add to Cart button pinned below the image

**Right column (info)**  
- Breadcrumb (Home › Category › Product)  
- Name, 4.2★ rating (static display)  
- MRP (calculated as `price / (1 - discount/100)`) struck through, price in bold, discount % in green  
- Highlights: description split on `.` and `!` into bullet points  
- Bank offers + EMI section  
- Pincode delivery checker (returns date in +3 days)  
- 4-icon services row (7 Day Return, Genuine Product, Free Delivery, 1 Year Warranty)

**Tabs section**  
- Description, Specifications (10-row table using product fields), Reviews (rating bars + 3 review cards)

**Similar Products**  
- Fetches 6 products from the same category, excludes current product, shows 4 as a grid

**Trade-offs accepted**  
- Discount percentage and MRP are calculated from a deterministic seed (not real data) — visually convincing but not business logic  
- Review data is static (no reviews API exists) — realistic placeholder  
- Gallery thumbnails are the same image with different filters — a real system would store multiple image URLs per product

---

## F-09 — AdminRoute Double Guard (Route + API)

**Context**  
Admin-only pages must be inaccessible to regular users. Frontend route guards can be bypassed by modifying localStorage or disabling JavaScript. Backend `@PreAuthorize("hasRole('ADMIN')")` is the authoritative enforcement — but that only returns an error after the API call. We also want to not render the admin UI at all for non-admins.

**Decision**  
Two layers of protection:

**Layer 1 — Frontend (UX guard)**  
`AdminRoute` component checks `isAdmin` from `AuthContext`. Non-admins are redirected to `/` before the page even renders. This is a UX convenience, not a security control.

```jsx
export default function AdminRoute({ children }) {
  const { token, isAdmin } = useAuth()
  if (!token) return <Navigate to="/login" replace />
  if (!isAdmin) return <Navigate to="/" replace />
  return children
}
```

**Layer 2 — Backend (real security)**  
Every admin API endpoint has `@PreAuthorize("hasRole('ADMIN')")` at the service layer. Even if someone bypasses the frontend guard and sends a raw HTTP request, the API returns 403. The JWT is verified cryptographically — roles cannot be faked without the secret.

The `⚙ Admin` navbar link is also conditionally rendered: `{isAdmin && <Link to="/admin">Admin</Link>}`. A regular user never sees the link.

**Trade-offs accepted**  
- `isAdmin` decoded from the JWT on the client — if the token is tampered with, the HMAC signature check at the backend will reject it regardless of what the client thinks  
- AdminRoute only checks once on render. 401/403 responses from the API should also trigger a redirect (handled in the Axios error interceptor)

---

## F-10 — Image Fallback via onError Handler

**Context**  
Product images come from external URLs (Unsplash). These URLs can fail for several reasons:
- URL is invalid or the photo was removed from Unsplash  
- Browser ORB (Opaque Response Blocking) blocks cross-origin image responses in some contexts  
- Network timeout  

A broken image shows a torn-image icon — ugly and confusing.

**Decision**  
A reusable `imgFallback` handler in `src/utils/format.js`:
```js
export const PRODUCT_PLACEHOLDER =
  "data:image/svg+xml,..." // dark SVG placeholder (shopping bag silhouette)

export const imgFallback = (e) => {
  e.target.onerror = null  // prevent infinite loop if placeholder also fails
  e.target.src = PRODUCT_PLACEHOLDER
}
```

Applied to every product `<img>`:
```jsx
<img src={product.imageUrl || PRODUCT_PLACEHOLDER} alt={product.name} onError={imgFallback} />
```

The placeholder is an inline SVG data URI — no network request, no CDN dependency, always available.

`e.target.onerror = null` is critical — without it, if `PRODUCT_PLACEHOLDER` itself fails (impossible for a data URI but defensive practice), the browser would call `imgFallback` again infinitely.

**Trade-offs accepted**  
- Placeholder is generic (same for all products) — a per-category placeholder would be more informative  
- The data URI adds ~500 bytes to the JS bundle — negligible
