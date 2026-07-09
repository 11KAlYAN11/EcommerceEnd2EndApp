# Phase F-3 — Product Listing & Detail

## WHY before code

### The Problem: Fetching data without blocking the UI

A React component **renders synchronously** — the function runs top-to-bottom and returns JSX immediately. But an API call is **async** — it might take 500ms or 2s. You cannot `await` inside the component render function.

```
❌ WRONG — this throws because render is sync
export default function ProductList() {
  const data = await axios.get('/api/products')  // SyntaxError
  return <div>{data}</div>
}
```

The solution: **`useState` + `useEffect`**

```
✅ RIGHT
export default function ProductList() {
  const [products, setProducts] = useState([])   // 1. Start empty

  useEffect(() => {                               // 2. Run AFTER first render
    axios.get('/api/products')
      .then(res => setProducts(res.data.content)) // 3. Re-render with data
  }, [])                                          // [] = only on mount

  return <div>{products.map(p => <div>{p.name}</div>)}</div>
}
```

**Timeline:**
1. Component renders → empty array → shows spinner
2. `useEffect` fires AFTER paint → API call starts
3. API returns → `setProducts(data)` → React re-renders → shows cards

---

## useEffect dependency array — the most important concept

```js
useEffect(fn, [])          // ← runs ONCE after mount (like componentDidMount)
useEffect(fn, [id])        // ← runs when `id` changes (route param changed)
useEffect(fn, [a, b])      // ← runs when either a or b changes
useEffect(fn)              // ← runs after EVERY render (usually a bug)
```

In `ProductListPage`, we fetch products when `search`, `categoryId`, or `page` changes:

```js
useEffect(() => {
  fetchProducts()
}, [search, categoryId, page])  // ← re-fetch when any filter changes
```

When user clicks a category → `setCategoryId(id)` → React sees dependency changed → re-runs effect → new API call → new products.

---

## Two-state search pattern

```js
const [searchInput, setSearchInput] = useState('')  // what's typed RIGHT NOW
const [search, setSearch] = useState('')            // what was actually submitted

function handleSearch(e) {
  e.preventDefault()
  setSearch(searchInput)   // only trigger API when user hits Search
  setPage(0)
}
```

WHY two states? If we used one state and called API on every keystroke:
- User types "l" → API call
- User types "la" → API call
- User types "lap" → API call
- User types "laptop" → API call

That's 6 API calls for one search. With two states, only 1 call when user submits.
(The professional version uses `debounce` — wait 300ms after last keystroke — but two-state is simpler to understand first.)

---

## Pagination with Spring Boot

Spring Boot returns a `Page<T>` object:

```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 13,
  "number": 0,        ← current page (0-indexed!)
  "size": 12,
  "first": true,
  "last": false
}
```

Our pagination uses `page` state (0-indexed, like Spring):

```js
const [page, setPage] = useState(0)

// Prev button
<button disabled={page === 0} onClick={() => setPage(p => p - 1)}>

// Page numbers
{[...Array(totalPages)].map((_, i) => (
  <button className={page === i ? 'active' : ''} onClick={() => setPage(i)}>
    {i + 1}   ← show 1-indexed to user
  </button>
))}
```

---

## Add to Cart flow (cross-component communication)

```
User clicks "Add to Cart"
  → handleAddToCart(product)
  → cartApi.addItem(product.id, 1)   ← POST /api/cart/items
  → refreshCart()                     ← re-fetches cart count (in CartContext)
  → toast.success(...)                ← global notification
```

`refreshCart()` comes from `CartContext`. The Navbar reads `itemCount` from that same context. So when we call `refreshCart()` in `ProductListPage`, the Navbar badge updates — without any prop passing.

---

## Interview Q&A — Phase F-3

**Q: What is the difference between controlled and uncontrolled inputs?**

Controlled: `value={state}` + `onChange={setState}`. React state is the source of truth. Predictable, testable.
Uncontrolled: the DOM stores the value (`useRef`). Good for performance in big forms but harder to validate.
Use controlled inputs by default.

**Q: Why does `useEffect` need a cleanup function sometimes?**

If an effect starts an async operation that sets state, and the component unmounts before the async finishes, React logs a warning: "Can't perform a state update on an unmounted component."

Fix:
```js
useEffect(() => {
  let cancelled = false
  fetchData().then(data => {
    if (!cancelled) setData(data)
  })
  return () => { cancelled = true }  // cleanup: runs when component unmounts
}, [])
```

**Q: What is prop drilling? How does Context solve it?**

Prop drilling = passing props through many intermediate components that don't need them.
```
App(user) → Sidebar(user) → UserCard(user) → Avatar(user)
```
Context = skip the middle layers. `Avatar` reads `useContext(AuthContext)` directly.

**Q: What is the difference between `page` and `size` in Spring pagination?**

`page` = which page (0-indexed). `size` = how many items per page. To get items 13–24, use `page=1&size=12`.

---

## Files written in this phase

- `src/pages/products/ProductListPage.jsx` — product grid with search + category filter + pagination
- `src/pages/products/ProductDetailPage.jsx` — single product view with quantity picker + add to cart
- `src/pages/products/Products.css` — dark card styles, sidebar, search bar, detail layout
