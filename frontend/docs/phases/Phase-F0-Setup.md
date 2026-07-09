# Phase F-0 — Project Setup & Why React

> **Learning Philosophy**: Never skip reasoning. Every tool chosen for a reason. Every folder named with intent.

---

## 1. Why a Frontend at All?

Your Spring Boot API returns JSON. JSON is data — not UI.
A browser cannot display `{ "name": "iPhone", "price": 99999 }` as a product card.
**Someone has to turn JSON → pixels.** That's the frontend's job.

---

## 2. Why React?

**The problem React solves**: keeping UI in sync with data.

### Old way (plain HTML + jQuery):
```
User clicks "Add to Cart"
→ you manually update cart count in navbar
→ you manually update button text
→ you manually update total price
→ miss one update = UI out of sync with actual data
```

### React way:
```
User clicks "Add to Cart"
→ you update ONE thing: cartItems state
→ React re-renders EVERY component that reads cartItems
→ navbar count, button text, total — all update automatically
```

**The core idea**: React is a **state → UI mapping**.

```
UI = f(state)
```

Your UI is a pure function of your application state.
Change the state → React figures out what changed → updates only those DOM nodes.
You never manually touch the DOM. React owns the DOM.

---

## 3. Why Vite (not Create React App)?

| | Create React App (CRA) | Vite |
|---|---|---|
| Dev server startup | 10–30 seconds | < 1 second |
| Hot reload | Slow (rebundles everything) | Instant (native ES modules) |
| Build tool | Webpack (old, slow) | Rollup (modern, fast) |
| Status in 2024 | Officially deprecated | Industry standard |

**Why Vite is fast**:
Browsers natively understand ES modules (`import/export` syntax).
- **Webpack**: bundles ALL your code into one file BEFORE serving → slow
- **Vite**: serves each file as-is, browser fetches only what it needs → instant

Hot reload replaces only the changed module, not the entire bundle.

---

## 4. Project Structure — Every Folder Has a Job

```
frontend/
├── src/
│   ├── api/              ← All HTTP calls (axios instances + API functions)
│   │                       WHY: URL changes = change ONE file not 50 components
│   │
│   ├── context/          ← React Context = global state (auth, cart)
│   │                       WHY: avoids prop drilling (passing data 5 levels deep)
│   │
│   ├── hooks/            ← Custom hooks = reusable stateful logic
│   │                       WHY: extract logic out of components → reusable, testable
│   │
│   ├── pages/            ← Full screens the user navigates to
│   │   ├── auth/         ← Login, Register
│   │   ├── products/     ← Product list, Product detail
│   │   ├── cart/         ← Cart page
│   │   ├── orders/       ← My orders, Order detail
│   │   ├── payments/     ← Payment flow
│   │   └── admin/        ← Dashboard, Product/Category CRUD, All orders
│   │
│   ├── components/       ← Reusable UI pieces (used across multiple pages)
│   │   ├── common/       ← Button, Spinner, Badge, Modal, Input
│   │   └── layout/       ← Navbar, Footer, ProtectedRoute, AdminRoute
│   │
│   └── utils/            ← Pure helper functions (formatPrice, formatDate)
│
├── docs/phases/          ← Phase-wise learning docs (like the backend)
└── index.html            ← The ONLY HTML file. React injects everything into #root
```

**Decision rules:**
- Can this UI piece be used on 2+ pages? → `components/`
- Is this a full screen with a URL? → `pages/`
- Does it make API calls? → logic goes in `api/`, not in the component
- Is it shared state (who is logged in)? → `context/`

---

## 5. Tech Stack Choices

| Library | Why |
|---|---|
| React 18 | Component model, hooks, massive ecosystem |
| Vite | Fast dev server, modern build |
| React Router v6 | Client-side navigation without page reloads |
| Axios | HTTP client — interceptors auto-attach JWT to every request |
| Plain CSS | Learn fundamentals first, no magic |

**What we're NOT adding (yet) and why:**
- ❌ Redux — overkill for this app. Context API is enough and teaches the concept
- ❌ TypeScript — you're learning React concepts first. Types come later
- ❌ Tailwind — want you to understand what CSS properties do, not just class names

---

## 6. How React Talks to Your Spring Boot Backend

```
Browser (React, port 5173)
    │
    │  axios.get("http://localhost:8080/api/products")
    ↓
Spring Boot (port 8080)
    │
    │  JSON response: { data: [...products] }
    ↓
React updates state → component re-renders → browser shows product cards
```

**CORS (Cross-Origin Resource Sharing)**:
Browser blocks requests from `localhost:5173` to `localhost:8080` by default.
Different port = different "origin" = browser's security policy fires.
Spring Boot must explicitly say: "I allow requests from port 5173."
We fix this in Phase F-2 (one annotation in Spring Boot).

---

## 7. Phase Roadmap

| Phase | What you build | New React concept |
|---|---|---|
| **F-0** | Setup, folder structure, app shell | Vite, JSX, project anatomy |
| **F-1** | Navbar, routing, page stubs | React Router, components, props |
| **F-2** | API layer + CORS fix | Axios, interceptors, useEffect, loading state |
| **F-3** | Login, Register, JWT, protected routes | Context API, useContext, localStorage |
| **F-4** | Product listing, search, pagination | Custom hooks, conditional rendering |
| **F-5** | Cart operations | Cart context, optimistic UI |
| **F-6** | Place order, order history | Multi-step flow |
| **F-7** | Payment simulation | UI state machine |
| **F-8** | Admin dashboard + CRUD | Forms, controlled inputs, role guards |
| **F-9** | Error boundaries, toasts, polish | Error handling, UX patterns |

---

## Interview Q&A — Phase F-0

**Q: What is the Virtual DOM and why does React use it?**
A: The real DOM is slow — any change can trigger browser layout recalculations across the whole tree. React keeps a Virtual DOM (lightweight JS object tree mirroring the real DOM). On state change, React diffs old vs new virtual DOM (reconciliation), finds the minimum set of changes, then applies only those to the real DOM. This batching approach is much faster than direct DOM manipulation.

**Q: What is JSX?**
A: JSX is syntactic sugar — `<Button text="Click" />` compiles to `React.createElement(Button, { text: "Click" })`. It's not HTML, it's JavaScript. That's why you write `className` (not `class`), `onClick` (not `onclick`), and `htmlFor` (not `for`). Vite's babel plugin handles the compilation.

**Q: What is a Single Page Application (SPA)?**
A: A traditional website sends a new HTML page from the server on every navigation. An SPA loads ONE HTML file + JS bundle. All subsequent navigation is handled client-side by JavaScript — only JSON data is fetched via API calls. Result: instant page transitions, no full page reloads. Trade-off: initial load is heavier, SEO needs SSR/SSG for crawlers.

**Q: Why Vite over Webpack/CRA?**
A: Vite uses native ES modules in dev — the browser fetches each file on demand, no bundling step needed. Webpack bundles everything first. For a 200-file project, Vite cold-starts in ~300ms; Webpack takes 30+ seconds. In prod both produce optimized bundles, but Vite uses Rollup (faster than Webpack 4).
